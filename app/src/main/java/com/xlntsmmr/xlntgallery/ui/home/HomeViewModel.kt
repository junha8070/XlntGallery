package com.xlntsmmr.xlntgallery.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xlntsmmr.xlntgallery.data.Photo
import com.xlntsmmr.xlntgallery.data.PhotoRepository
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.AiModels
import com.xlntsmmr.xlntgallery.data.ai.ModelDownloadWorker
import com.xlntsmmr.xlntgallery.data.db.GalleryDatabase
import com.xlntsmmr.xlntgallery.data.index.IndexingScheduler
import com.xlntsmmr.xlntgallery.data.gemini.GeminiNanoTagger
import com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus
import com.xlntsmmr.xlntgallery.data.gemini.GeminiTaggingRunner
import com.xlntsmmr.xlntgallery.data.index.PhotoIndexer
import com.xlntsmmr.xlntgallery.data.search.MatchType
import com.xlntsmmr.xlntgallery.data.search.SearchHit
import com.xlntsmmr.xlntgallery.data.search.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

// 검색 결과 상태
sealed interface SearchUiState {
    data object Idle : SearchUiState                          // 검색어 없음 → 전체 사진
    data object Searching : SearchUiState
    data class Results(val hits: List<SearchHit>) : SearchUiState
}

// 사진 분석(인덱싱) 진행 상태
data class IndexStatus(
    val total: Int = 0,
    val ocrDone: Int = 0,
    val siglipDone: Int = 0,
    val clipDone: Int = 0,
    val isRunning: Boolean = false,
    val modelsReady: Boolean = false,
    val modelDownload: Float? = null,   // null = 없음, 0~1 = 진행률, -1 = Wi-Fi 대기
    val geminiTagged: Int = 0,
    val geminiRunning: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val appContext: Context,
    private val repository: PhotoRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    // ── 검색 ─────────────────────────────────────────
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(MatchType.entries.toSet())

    val searchState: StateFlow<SearchUiState> =
        combine(query, filters) { q, f -> q.trim() to f }
            .debounce(300)   // 타이핑이 0.3초 멈추면 검색 (글자마다 검색하지 않게)
            .transformLatest { (q, f) ->
                // transformLatest: 새 검색어가 오면 이전 검색은 취소
                if (q.isEmpty()) {
                    emit(SearchUiState.Idle)
                } else {
                    emit(SearchUiState.Searching)
                    emit(SearchUiState.Results(searchRepository.search(q, f)))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun setFilters(types: Set<MatchType>) {
        filters.value = types
    }

    // ── 분석 상태 ─────────────────────────────────────
    // 분석 진행 상황 (OCR · SigLIP 2 · CLIP · Gemini) + 모델 다운로드 상태
    val indexStatus: StateFlow<IndexStatus> = run {
        val dao = GalleryDatabase.get(appContext).galleryDao()
        combine(
            dao.observeStats(
                PhotoIndexer.OCR_VERSION,
                AiModels.modelId(AiModelType.SIGLIP2),
                AiModels.modelId(AiModelType.CLIP)
            ),
            IndexingScheduler.isRunning(appContext),
            dao.observeTagStats(GeminiNanoTagger.TAG_VERSION),
            GeminiTaggingRunner.status,
            ModelDownloadWorker.progress(appContext)
        ) { stats, running, tags, gemini, download ->
            IndexStatus(
                total = stats.total,
                ocrDone = stats.ocrDone,
                siglipDone = stats.siglipDone,
                clipDone = stats.clipDone,
                isRunning = running,
                modelsReady = AiModels.allReady(appContext),
                modelDownload = download,
                geminiTagged = tags.tagged,
                geminiRunning = gemini is GeminiStatus.Running
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexStatus())
    }

    // 이미 불러왔으면 다시 안 불러옴 (화면 회전 시 LaunchedEffect가 또 호출하기 때문)
    private var isLoaded = false

    fun loadPhotos() {
        if (isLoaded) return
        isLoaded = true
        viewModelScope.launch {
            _photos.value = repository.loadPhotos()
        }
        viewModelScope.launch {
            // 권한이 생겼으니 백그라운드 분석 시작 (이미 돌고 있으면 그대로) + 앱을 닫은 뒤를 위한 예약
            IndexingScheduler.start(appContext)
            // AI 모델(SigLIP 2·CLIP)이 없으면 자동 다운로드 예약 (기본: Wi-Fi에서만)
            ModelDownloadWorker.ensure(appContext)
        }
    }

    override fun onCleared() {
        searchRepository.close()
    }

    // "HomeViewModel은 이렇게 만들어라"는 설명서
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // APPLICATION_KEY로 Application(=Context)을 꺼낼 수 있음
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                HomeViewModel(app, PhotoRepository(app), SearchRepository(app))
            }
        }
    }
}
