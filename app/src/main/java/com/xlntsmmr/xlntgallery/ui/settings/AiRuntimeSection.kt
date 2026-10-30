package com.xlntsmmr.xlntgallery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xlntsmmr.xlntgallery.data.ai.AiBenchmark
import com.xlntsmmr.xlntgallery.data.ai.BenchmarkResult
import com.xlntsmmr.xlntgallery.data.ai.HardwareAccelerator
import com.xlntsmmr.xlntgallery.data.ai.RuntimeRecord
import com.xlntsmmr.xlntgallery.ui.components.MainBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBackground = Color(0xFFF2F4F7)
private val TextPrimary = Color(0xFF16181D)
private val TextSecondary = Color(0xFF6B7280)
private val AcceleratorColor = Color(0xFF00A86B)   // 가속기(NNAPI) 처리분
private val CpuColor = Color(0xFFB0B7C3)           // CPU 처리분
private val WarningColor = Color(0xFFD97706)

/** 설정 화면: 지금 AI가 실제로 어떤 장치에서 돌고 있는지 + 성능 테스트 */
@Composable
fun AiRuntimeSection(
    records: Map<String, RuntimeRecord>,
    benchmark: BenchmarkState,
    aiModelReady: Boolean,
    onRunBenchmark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ① 지금 실행 중
        Text("지금 실행 중인 장치", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (records.isEmpty()) {
            Text(
                "아직 AI 모델이 실행되지 않았어요. 사진 분석이나 AI 검색을 하면 여기에 표시돼요.",
                fontSize = 12.sp, color = TextSecondary
            )
        } else {
            records.values.sortedBy { it.modelLabel }.forEach { record -> RuntimeRow(record) }
        }

        // ② 성능 테스트
        Spacer(Modifier.height(4.dp))
        Text("AI 성능 테스트", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "같은 사진을 NPU 우선 · GPU 우선 · CPU로 각각 분석해서, 속도와 \"연산 중 몇 %를 가속기가 처리했는지\"를 측정해요.",
            fontSize = 12.sp, color = TextSecondary
        )

        when (benchmark) {
            is BenchmarkState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MainBlue)
                Spacer(Modifier.width(8.dp))
                Text(benchmark.step, fontSize = 13.sp, color = MainBlue)
            }
            is BenchmarkState.Failed -> Text("테스트 실패: ${benchmark.message}", fontSize = 12.sp, color = WarningColor)
            is BenchmarkState.Done -> {
                benchmark.results.forEach { BenchmarkRow(it) }
                Verdict(benchmark.results)
            }
            BenchmarkState.Idle -> Unit
        }

        val running = benchmark is BenchmarkState.Running
        Text(
            text = when {
                !aiModelReady -> "AI 검색 모델을 먼저 받아주세요"
                benchmark is BenchmarkState.Done -> "다시 테스트"
                else -> "테스트 시작"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (aiModelReady && !running) Color.White else TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (aiModelReady && !running) MainBlue else Color(0xFFE2E5EA))
                .clickable(enabled = aiModelReady && !running, onClick = onRunBenchmark)
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Text(
            "※ NNAPI 가속기 안에서 NPU와 GPU 중 무엇을 썼는지는 Android가 앱에 알려주지 않아요. " +
                "가속기 처리 비율이 높고 CPU보다 빠르면 NPU/GPU가 실제로 일하고 있는 거예요.",
            fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp
        )
    }
}

@Composable
private fun RuntimeRow(record: RuntimeRecord) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(record.timeMillis))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(record.modelLabel, fontSize = 13.sp, color = TextPrimary)
            Text(
                "설정: ${AiBenchmark.label(record.requested)} · $time",
                fontSize = 11.sp, color = TextSecondary
            )
            if (record.fellBack) {
                Text("원하는 가속기를 쓸 수 없어 CPU로 대체됐어요", fontSize = 11.sp, color = WarningColor)
            }
        }
        BackendChip(record.backend)
    }
}

@Composable
private fun BenchmarkRow(result: BenchmarkResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AiBenchmark.label(result.accelerator),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (result.error == null) BackendChip(result.backend)
        }

        if (result.error != null) {
            Text("실행 실패: ${result.error}", fontSize = 12.sp, color = WarningColor)
            return@Column
        }

        Text(
            "장당 %.0f ms  (첫 실행 %.0f ms, 가속기 준비 시간 포함)".format(result.averageMs, result.firstRunMs),
            fontSize = 12.sp, color = TextPrimary
        )

        // 가속기 vs CPU 처리 비율 막대
        val share = result.acceleratorShare.coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CpuColor)
        ) {
            if (share > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(share)
                        .background(AcceleratorColor)
                )
            }
            if (share < 1f) Spacer(Modifier.weight(1f - share))
        }
        Text(
            "가속기(NNAPI) ${(share * 100).toInt()}% · CPU ${100 - (share * 100).toInt()}%",
            fontSize = 11.sp,
            color = if (share > 0f) AcceleratorColor else TextSecondary
        )
        if (result.fellBack) {
            Text("가속기를 쓸 수 없어 CPU로 대체됨", fontSize = 11.sp, color = WarningColor)
        }
    }
}

// 결과 한 줄 요약
@Composable
private fun Verdict(results: List<BenchmarkResult>) {
    val ok = results.filter { it.error == null }
    val cpu = ok.firstOrNull { it.accelerator == HardwareAccelerator.CPU }
    val bestAccelerated = ok
        .filter { it.accelerator != HardwareAccelerator.CPU && it.acceleratorShare >= 0.5f }
        .minByOrNull { it.averageMs }
    val fastest = ok.minByOrNull { it.averageMs }

    val message = when {
        bestAccelerated != null && cpu != null && bestAccelerated.averageMs < cpu.averageMs ->
            "✅ ${AiBenchmark.label(bestAccelerated.accelerator)}에서 가속기가 실제로 동작하고, CPU보다 %.1f배 빨라요."
                .format(cpu.averageMs / bestAccelerated.averageMs)
        ok.all { it.acceleratorShare < 0.05f } ->
            "⚠️ 이 기기에서는 가속기(NPU/GPU)가 거의 쓰이지 않고 CPU가 처리하고 있어요."
        else ->
            "ℹ️ 가속기가 일부 연산을 처리하지만 CPU보다 빠르지 않아요."
    }
    val recommend = fastest?.let { " 가장 빠른 설정: ${AiBenchmark.label(it.accelerator)}" }.orEmpty()

    Text(
        message + recommend,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        lineHeight = 18.sp
    )
}

@Composable
private fun BackendChip(backend: String) {
    val isAccelerator = backend.contains("NNAPI")
    Text(
        text = backend,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (isAccelerator) AcceleratorColor else Color(0xFF424956),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAccelerator) Color(0xFFE3F6EE) else Color(0xFFEDEFF2))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
