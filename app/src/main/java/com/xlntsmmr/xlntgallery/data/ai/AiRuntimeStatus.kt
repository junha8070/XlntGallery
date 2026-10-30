package com.xlntsmmr.xlntgallery.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AI 모델 하나가 실제로 어떤 방식으로 실행됐는지 */
data class RuntimeRecord(
    val modelLabel: String,                  // "사진 AI (이미지)"
    val requested: HardwareAccelerator,      // 설정에서 고른 것
    val backend: String,                     // 실제로 만들어진 실행 방식
    val fellBack: Boolean,                   // 원하는 가속기가 안 돼서 CPU로 대체됐는지
    val timeMillis: Long = System.currentTimeMillis()
)

/**
 * 앱 전체에서 공유하는 "AI 실행 상태 게시판".
 * 모델 세션이 만들어질 때마다 기록 → 설정 화면이 구독해서 실시간으로 보여줌.
 * (같은 앱 프로세스 안의 백그라운드 분석 작업도 여기에 기록됨)
 */
object AiRuntimeStatus {
    private val _records = MutableStateFlow<Map<String, RuntimeRecord>>(emptyMap())
    val records: StateFlow<Map<String, RuntimeRecord>> = _records.asStateFlow()

    fun report(record: RuntimeRecord) {
        _records.update { it + (record.modelLabel to record) }
    }
}
