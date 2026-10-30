package com.xlntsmmr.xlntgallery.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xlntsmmr.xlntgallery.data.MlKitOcrEngine
import com.xlntsmmr.xlntgallery.data.OcrBox
import com.xlntsmmr.xlntgallery.data.OcrEngine
import com.xlntsmmr.xlntgallery.data.OcrEngineType
import com.xlntsmmr.xlntgallery.data.OcrLine
import com.xlntsmmr.xlntgallery.data.OcrResult
import com.xlntsmmr.xlntgallery.data.PaddleModelManager
import com.xlntsmmr.xlntgallery.data.PaddleOcrEngine
import com.xlntsmmr.xlntgallery.data.PhotoDetail
import com.xlntsmmr.xlntgallery.data.PhotoRepository
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 길게 눌러 선택할 수 있는 단어 (읽는 순서대로 정렬된 목록의 한 칸)
data class SelectableWord(
    val text: String,
    val box: OcrBox,
    val lineIndex: Int   // 몇 번째 줄인지 → 복사할 때 줄이 바뀌면 줄바꿈
)

// OCR 진행 상태: 셋 중 하나만 가능 → sealed interface
sealed interface OcrState {
    data object Loading : OcrState
    data class Done(
        val items: List<CopyableInfo>,
        val lines: List<OcrLine>,           // 사진 위에 그릴 글자 영역
        val words: List<SelectableWord>,    // 길게 눌러 선택용
        val engineName: String,             // 어떤 엔진으로 읽었는지
        val usedFallback: Boolean = false,  // PaddleOCR을 골랐지만 모델이 없어 ML Kit으로 대신했는지
        val isRefining: Boolean = false     // 2단계(정밀 재인식) 진행 중
    ) : OcrState
    data class Failed(val message: String) : OcrState
}

// 화면 전체 상태를 한 객체로
data class PhotoDetailUiState(
    val isLoading: Boolean = true,
    val detail: PhotoDetail? = null,
    val ocr: OcrState = OcrState.Loading
)

class PhotoDetailViewModel(
    private val photoId: Long,
    private val appContext: Context,
    private val photoRepository: PhotoRepository,
    private val settings: SettingsRepository,
    private val paddleModels: PaddleModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    private var engine: OcrEngine? = null

    init {
        viewModelScope.launch {
            val detail = photoRepository.loadPhotoDetail(photoId)
            _uiState.update { it.copy(isLoading = false, detail = detail) }
            if (detail == null) return@launch

            // OCR과 주소 변환은 서로 기다릴 필요 없음 → 각각 동시에 실행
            launch { runOcr(detail) }
            if (detail.latitude != null && detail.longitude != null) {
                launch { loadAddress(detail.latitude, detail.longitude) }
            }
        }
    }

    private suspend fun runOcr(detail: PhotoDetail) {
        // 설정에서 고른 엔진. PaddleOCR 모델이 아직 없으면 ML Kit으로 대신
        val wanted = settings.ocrEngine.first()
        val usePaddle = wanted == OcrEngineType.PADDLE && paddleModels.isReady()
        val ocrEngine: OcrEngine =
            if (usePaddle) PaddleOcrEngine(appContext, paddleModels, settings.accelerator.first())
            else MlKitOcrEngine(appContext)
        engine = ocrEngine
        val usedFallback = wanted == OcrEngineType.PADDLE && !usePaddle

        fun done(result: OcrResult, refining: Boolean) = OcrState.Done(
            items = InfoExtractor.extract(result),
            lines = result.lines,
            words = readingOrderWords(result),
            engineName = ocrEngine.type.displayName,
            usedFallback = usedFallback,
            isRefining = refining
        )

        // 1단계: 사진 전체 인식 → 바로 화면에 보여줌
        val first = try {
            ocrEngine.recognize(detail.uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // 화면을 나가서 취소된 건 실패가 아님 → 그대로 전달
        } catch (e: Exception) {
            _uiState.update { it.copy(ocr = OcrState.Failed(e.message ?: "알 수 없는 오류")) }
            return
        }
        _uiState.update { it.copy(ocr = done(first, refining = true)) }

        // 2단계: 엔진별 정밀 보정 (ML Kit: 블록별 확대 재인식 / Paddle: 없음)
        val refined = try {
            ocrEngine.refine(detail.uri, first)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            first   // 2단계가 실패해도 1단계 결과는 그대로 유지
        }
        _uiState.update { it.copy(ocr = done(refined, refining = false)) }
    }

    // 블록(위→아래, 왼→오) → 줄 → 단어 순서로 펼침
    private fun readingOrderWords(result: OcrResult): List<SelectableWord> {
        var lineIndex = 0
        return result.blocks
            .sortedWith(compareBy({ block -> block.bounds.top }, { block -> block.bounds.left }))
            .flatMap { block -> block.lines }
            .flatMap { line ->
                val index = lineIndex++
                line.words.map { word -> SelectableWord(word.text, word.box, index) }
            }
    }

    private suspend fun loadAddress(lat: Double, lng: Double) {
        val address = photoRepository.reverseGeocode(lat, lng) ?: return
        _uiState.update { state -> state.copy(detail = state.detail?.copy(address = address)) }
    }

    override fun onCleared() {
        engine?.close()
    }

    companion object {
        // 내비게이션 경로의 {photoId}와 같은 이름
        const val ARG_PHOTO_ID = "photoId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                // 내비게이션으로 넘어온 인자는 SavedStateHandle에 들어 있음
                val photoId = createSavedStateHandle().get<Long>(ARG_PHOTO_ID)!!
                PhotoDetailViewModel(
                    photoId = photoId,
                    appContext = app,
                    photoRepository = PhotoRepository(app),
                    settings = SettingsRepository(app),
                    paddleModels = PaddleModelManager(app)
                )
            }
        }
    }
}
