package com.xlntsmmr.xlntgallery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xlntsmmr.xlntgallery.data.OcrEngineType
import com.xlntsmmr.xlntgallery.data.PaddleModelManager
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import com.xlntsmmr.xlntgallery.data.ai.AiBenchmark
import com.xlntsmmr.xlntgallery.data.ai.AiRuntimeStatus
import com.xlntsmmr.xlntgallery.data.ai.BenchmarkResult
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.AiModels
import com.xlntsmmr.xlntgallery.data.ai.ModelDownloadWorker
import com.xlntsmmr.xlntgallery.data.ai.RuntimeRecord
import com.xlntsmmr.xlntgallery.data.db.GalleryDatabase
import com.xlntsmmr.xlntgallery.data.db.TagStats
import com.xlntsmmr.xlntgallery.data.gemini.GeminiNanoTagger
import com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus
import com.xlntsmmr.xlntgallery.data.gemini.GeminiTaggingRunner
import com.xlntsmmr.xlntgallery.data.ai.HardwareAccelerator
import com.xlntsmmr.xlntgallery.data.index.IndexingScheduler
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// PaddleOCR 모델 상태
sealed interface PaddleModelState {
    data object NotDownloaded : PaddleModelState
    data class Downloading(val progress: Float) : PaddleModelState
    data object Ready : PaddleModelState
    data class Failed(val message: String) : PaddleModelState
}

// AI 성능 테스트 상태
sealed interface BenchmarkState {
    data object Idle : BenchmarkState
    data class Running(val step: String) : BenchmarkState
    data class Done(val results: List<BenchmarkResult>) : BenchmarkState
    data class Failed(val message: String) : BenchmarkState
}

data class SettingsUiState(
    val ocrEngine: OcrEngineType = OcrEngineType.ML_KIT,
    val paddleModel: PaddleModelState = PaddleModelState.NotDownloaded,
    val accelerator: HardwareAccelerator = HardwareAccelerator.AUTO,
    val modelsReady: Map<AiModelType, Boolean> = emptyMap(),   // SigLIP 2 / CLIP 설치 여부
    val modelDownload: Float? = null,                          // null = 없음, 0~1 = 진행률, -1 = Wi-Fi 대기
    val modelsOnMobileData: Boolean = false,
    val analyzeChargingOnly: Boolean = false,
    val autoAnalyzeNewPhotos: Boolean = true
) {
    val aiModelReady: Boolean get() = modelsReady.values.any { it }
}

class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val paddleModels: PaddleModelManager
) : ViewModel() {


    private val paddleModelState = MutableStateFlow(
        if (paddleModels.isReady()) PaddleModelState.Ready else PaddleModelState.NotDownloaded
    )

    // 저장된 설정(Flow) + 다운로드 상태(Flow) → 화면 상태 하나로 합침
    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(settings.ocrEngine, paddleModelState, settings.accelerator) { e, m, a -> Triple(e, m, a) },
            ModelDownloadWorker.progress(appContext),
            settings.modelsOnMobileData,
            settings.analyzeChargingOnly,
            settings.autoAnalyzeNewPhotos
        ) { (engine, model, accelerator), download, mobileData, chargingOnly, autoNew ->
            SettingsUiState(
                ocrEngine = engine,
                paddleModel = model,
                accelerator = accelerator,
                modelsReady = AiModels.ALL.associateWith { AiModels.pack(appContext, it).isReady() },
                modelDownload = download,
                modelsOnMobileData = mobileData,
                analyzeChargingOnly = chargingOnly,
                autoAnalyzeNewPhotos = autoNew
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),   // 화면을 떠나고 5초 뒤 구독 중지
            initialValue = SettingsUiState(paddleModel = paddleModelState.value)
        )

    // ── Gemini Nano ─────────────────────────────────
    val geminiEnabled: StateFlow<Boolean> =
        settings.geminiTagging.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val geminiStatus: StateFlow<GeminiStatus> = GeminiTaggingRunner.status
    val tagStats: StateFlow<TagStats> =
        GalleryDatabase.get(appContext).galleryDao()
            .observeTagStats(GeminiNanoTagger.TAG_VERSION)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagStats(0, 0))

    fun setGeminiEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setGeminiTagging(enabled) }
    }

    // 지금 실제로 실행된 AI 모델들과 그 실행 방식 (앱 전체 게시판을 그대로 노출)
    val runtimeRecords: StateFlow<Map<String, RuntimeRecord>> = AiRuntimeStatus.records

    private val _benchmark = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmark: StateFlow<BenchmarkState> = _benchmark

    fun runBenchmark() {
        if (_benchmark.value is BenchmarkState.Running) return
        viewModelScope.launch {
            _benchmark.value = BenchmarkState.Running("준비 중…")
            _benchmark.value = try {
                BenchmarkState.Done(
                    AiBenchmark(appContext).run { step -> _benchmark.value = BenchmarkState.Running(step) }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                BenchmarkState.Failed(e.message ?: "알 수 없는 오류")
            }
        }
    }

    fun selectAccelerator(accelerator: HardwareAccelerator) {
        viewModelScope.launch { settings.setAccelerator(accelerator) }
    }

    /** 모바일 데이터로도 모델 받기 → 다운로드 조건을 바꿔서 다시 예약 */
    fun setModelsOnMobileData(enabled: Boolean) {
        viewModelScope.launch {
            settings.setModelsOnMobileData(enabled)
            ModelDownloadWorker.ensure(appContext, replace = true)
        }
    }

    /** 다운로드 실패 후 다시 시도 */
    fun retryModelDownload() {
        viewModelScope.launch { ModelDownloadWorker.ensure(appContext, replace = true) }
    }

    fun setAnalyzeChargingOnly(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAnalyzeChargingOnly(enabled)
            IndexingScheduler.start(appContext)   // 예약 조건을 새 설정으로 갱신
        }
    }

    fun setAutoAnalyzeNewPhotos(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoAnalyzeNewPhotos(enabled)
            IndexingScheduler.watchNewPhotos(appContext)
        }
    }

    fun selectOcrEngine(type: OcrEngineType) {
        viewModelScope.launch { settings.setOcrEngine(type) }
        // PaddleOCR을 골랐는데 모델이 없으면 바로 다운로드 시작
        if (type == OcrEngineType.PADDLE) {
            val state = paddleModelState.value
            if (state is PaddleModelState.NotDownloaded || state is PaddleModelState.Failed) {
                downloadPaddleModels()
            }
        }
    }

    private fun downloadPaddleModels() {
        viewModelScope.launch {
            paddleModelState.value = PaddleModelState.Downloading(0f)
            try {
                paddleModels.download().collect { progress ->
                    paddleModelState.value = PaddleModelState.Downloading(progress)
                }
                paddleModelState.value = PaddleModelState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                paddleModelState.value = PaddleModelState.NotDownloaded
                throw e
            } catch (e: Exception) {
                paddleModelState.value = PaddleModelState.Failed(e.message ?: "알 수 없는 오류")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SettingsViewModel(app, SettingsRepository(app), PaddleModelManager(app))
            }
        }
    }
}
