package com.xlntsmmr.xlntgallery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xlntsmmr.xlntgallery.data.OcrEngineType
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.HardwareAccelerator
import androidx.compose.material.icons.outlined.Psychology
import com.xlntsmmr.xlntgallery.ui.components.MainBlue

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    // 저장되는 설정 (DataStore → ViewModel → 화면)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val runtimeRecords by viewModel.runtimeRecords.collectAsStateWithLifecycle()
    val benchmark by viewModel.benchmark.collectAsStateWithLifecycle()
    val geminiEnabled by viewModel.geminiEnabled.collectAsStateWithLifecycle()
    val geminiStatus by viewModel.geminiStatus.collectAsStateWithLifecycle()
    val tagStats by viewModel.tagStats.collectAsStateWithLifecycle()

    // AI 실행 장치: 이제 DataStore에 저장됨 (앱을 껐다 켜도 유지)
    val selectedHardware = uiState.accelerator.name

//    var aiSearchEnabled by rememberSaveable {
//        mutableStateOf(true)
//    }
//
//    var ocrEnabled by rememberSaveable {
//        mutableStateOf(true)
//    }

    var privacyFilterEnabled by rememberSaveable {
        mutableStateOf(true)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // ① AI 분석 설정

// ① AI 분석 설정
        SettingsSection(
            title = "AI 분석 설정",
            description = "기기 성능에 맞춰 AI 분석 방식을 선택해주세요."
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF2F4F7),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(8.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                SelectHardwareChip(
                    imageVector = Icons.Outlined.AutoAwesome,
                    title = "자동",
                    content = "배터리 및 발열 상태에 맞춘 최적화 가속",
                    selected = selectedHardware == "AUTO",
                    recommended = true,
                    onClick = {
                        viewModel.selectAccelerator(HardwareAccelerator.AUTO)
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.Memory,
                    title = "NPU 우선",
                    content = "Galaxy 온디바이스 NPU 가속 엔진 사용",
                    selected = selectedHardware == "NPU",
                    onClick = {
                        viewModel.selectAccelerator(HardwareAccelerator.NPU)
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.Speed,
                    title = "GPU 우선",
                    content = "고속 그래픽 병렬 처리 가속",
                    selected = selectedHardware == "GPU",
                    onClick = {
                        viewModel.selectAccelerator(HardwareAccelerator.GPU)
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.DeveloperBoard,
                    title = "CPU",
                    content = "표준 연산 유닛 기반 안정 모드",
                    selected = selectedHardware == "CPU",
                    onClick = {
                        viewModel.selectAccelerator(HardwareAccelerator.CPU)
                    }
                )
            }
        }


        // ①-2 AI 실행 상태 (실제로 어떤 장치에서 돌고 있는지)
        SettingsSection(
            title = "AI 실행 상태",
            description = "설정한 장치가 실제로 쓰이고 있는지 확인해요."
        ) {
            AiRuntimeSection(
                records = runtimeRecords,
                benchmark = benchmark,
                aiModelReady = uiState.aiModelReady,
                onRunBenchmark = { viewModel.runBenchmark() }
            )
        }

        // ② 글자 인식(OCR) 엔진
        SettingsSection(
            title = "글자 인식(OCR) 엔진",
            description = "사진 속 글자를 읽을 엔진을 선택해주세요."
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF2F4F7),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(8.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SelectHardwareChip(
                    imageVector = Icons.Outlined.DocumentScanner,
                    title = "ML Kit",
                    content = "Google 온디바이스 OCR · 앱에 내장, 빠름",
                    selected = uiState.ocrEngine == OcrEngineType.ML_KIT,
                    recommended = true,
                    onClick = { viewModel.selectOcrEngine(OcrEngineType.ML_KIT) }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.TextFields,
                    title = "PaddleOCR",
                    content = when (val model = uiState.paddleModel) {
                        PaddleModelState.Ready -> "PP-OCRv5 한국어 모델 · 설치됨"
                        PaddleModelState.NotDownloaded -> "PP-OCRv5 한국어 모델 · 선택하면 약 18MB 다운로드"
                        is PaddleModelState.Downloading -> "모델 다운로드 중 ${(model.progress * 100).toInt()}%"
                        is PaddleModelState.Failed -> "다운로드 실패 · 다시 누르면 재시도 (${model.message})"
                    },
                    selected = uiState.ocrEngine == OcrEngineType.PADDLE,
                    onClick = { viewModel.selectOcrEngine(OcrEngineType.PADDLE) }
                )

                val model = uiState.paddleModel
                if (model is PaddleModelState.Downloading) {
                    LinearProgressIndicator(
                        progress = { model.progress },
                        color = MainBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ③-1 Gemini Nano 사진 이해 (기본)
        SettingsSection(
            title = "Gemini Nano 사진 이해",
            description = "Google 온디바이스 AI가 사진을 보고 \"계약서\", \"영수증\"처럼 종류와 키워드를 붙여요. 기기 안에서만 동작해요."
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFFF2F4F7), shape = RoundedCornerShape(20.dp))
                    .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingSwitch(
                    title = "Gemini Nano로 분석 (기본)",
                    content = "앱을 켜 두는 동안 글자가 많은 사진부터 분석해요. 앱을 닫으면 멈췄다가 다음에 이어서 해요.",
                    checked = geminiEnabled,
                    onCheckedChange = { viewModel.setGeminiEnabled(it) }
                )
                Text(
                    text = geminiStatusText(geminiStatus, tagStats),
                    fontSize = 12.sp,
                    color = MainBlue
                )
                if (tagStats.total > 0) {
                    LinearProgressIndicator(
                        progress = { tagStats.tagged / tagStats.total.toFloat() },
                        color = MainBlue,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp)
                    )
                }
            }
        }

        // ③ AI 검색 모델 (SigLIP 2 + CLIP 함께 사용, 자동 다운로드)
        SettingsSection(
            title = "AI 검색 모델",
            description = "사진 내용을 이해하는 두 모델을 함께 써서 서로 교차 확인해요. 앱을 처음 열면 자동으로 받고, 기기 안에서만 동작해요."
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFFF2F4F7), shape = RoundedCornerShape(20.dp))
                    .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiModelType.entries.forEach { type ->
                    val ready = uiState.modelsReady[type] == true
                    Text(
                        text = "${type.displayName} · ${type.description} · " + if (ready) "설치됨" else "준비 중",
                        fontSize = 13.sp,
                        color = if (ready) Color(0xFF16181D) else Color(0xFF6B7280)
                    )
                }
                val download = uiState.modelDownload
                when {
                    download != null && download >= 0f -> {
                        Text("다운로드 중 ${(download * 100).toInt()}%", fontSize = 12.sp, color = MainBlue)
                        LinearProgressIndicator(
                            progress = { download },
                            color = MainBlue,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                        )
                    }
                    download != null ->
                        Text("Wi-Fi에 연결되면 자동으로 받아요", fontSize = 12.sp, color = MainBlue)
                    !uiState.modelsReady.values.all { it } ->
                        Text(
                            text = "다시 받기",
                            fontSize = 13.sp,
                            color = MainBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.retryModelDownload() }
                                .padding(vertical = 6.dp)
                        )
                }
                SettingSwitch(
                    title = "모바일 데이터로도 받기",
                    content = "약 600MB예요. 끄면 Wi-Fi에 연결됐을 때만 받아요. (데이터 요금 주의)",
                    checked = uiState.modelsOnMobileData,
                    onCheckedChange = { viewModel.setModelsOnMobileData(it) }
                )
            }
        }

        // ④ 검색 정확도
//        SettingsSection(
//            title = "검색 정확도",
//            description = "AI 검색과 문자 검색의 동작을 설정합니다."
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .background(
//                        Color(0xFFF2F4F7),
//                        RoundedCornerShape(12.dp)
//                    )
//                    .padding(12.dp)
//            ) {
//                SettingSwitch(
//                    title = "AI 의미 검색",
//                    checked = aiSearchEnabled,
//                    onCheckedChange = {
//                        aiSearchEnabled = it
//                    }
//                )
//
//                SettingSwitch(
//                    title = "OCR 문자 검색",
//                    checked = ocrEnabled,
//                    onCheckedChange = {
//                        ocrEnabled = it
//                    }
//                )
//            }
//        }

        // ③ 개인정보 필터
        SettingsSection(
            title = "보안",
            description = "당신의 정보는 중요해요."
        ) {
            Box(modifier = Modifier
                .background(
                    color = Color(0xFFF2F4F7),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 12.dp)){

                SettingSwitch(
                    title = "개인정보 마스킹 필터",
                    content = "검색 결과와 상세 화면에서 민감 정보를 자동으로 마스킹해요.",
                    checked = privacyFilterEnabled,
                    onCheckedChange = {
                        privacyFilterEnabled = it
                    }
                )
            }

        }

        // ④ 사진 분석
        SettingsSection(
            title = "사진 분석",
            description = "사진을 분석하고 검색에 활용해요."
        ) {
            Column (modifier = Modifier
                .background(
                    color = Color(0xFFF2F4F7),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 12.dp)){

                SettingSwitch(
                    title = "충전 중에만 분석",
                    content = "앱을 닫았을 때는 충전 중에만 분석해요. 끄면 배터리가 넉넉할 때 분석해요. (Gemini Nano는 앱을 열었을 때만 동작)",
                    checked = uiState.analyzeChargingOnly,
                    onCheckedChange = { viewModel.setAnalyzeChargingOnly(it) }
                )

                SettingSwitch(
                    title = "새 사진 자동 분석",
                    content = "새로 촬영하거나 저장한 사진을 앱을 열지 않아도 분석해요.",
                    checked = uiState.autoAnalyzeNewPhotos,
                    onCheckedChange = { viewModel.setAutoAnalyzeNewPhotos(it) }
                )
            }
        }

//        // ⑤ 검색 데이터 관리
//        SettingsSection(
//            title = "검색 데이터 관리",
//            description = "검색 인덱스를 다시 생성하거나 삭제할 수 있습니다."
//        ) {
//            Text("재분석 / 검색 인덱스 삭제")
//        }
//
        Spacer(
            modifier = Modifier.height(110.dp)
        )
    }
}

// Gemini Nano 상태 문구
private fun geminiStatusText(
    status: com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus,
    stats: com.xlntsmmr.xlntgallery.data.db.TagStats
): String {
    val progress = "분석 ${stats.tagged}/${stats.total}장"
    return when (status) {
        com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Idle -> "대기 중 · $progress"
        com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Checking -> "Gemini Nano 확인 중…"
        com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Disabled -> "꺼져 있어요 · $progress"
        com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Unsupported ->
            "이 기기는 Gemini Nano를 지원하지 않아요. AI 검색 모델(SigLIP 2/CLIP)만 사용해요."
        is com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Downloading ->
            "시스템이 Gemini Nano를 내려받는 중" + (status.progress?.let { " ${(it * 100).toInt()}%" } ?: "…")
        is com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Running -> "분석 중 · $progress"
        com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Finished -> "분석할 사진을 기다리는 중 · $progress (OCR이 끝난 사진부터 분석)"
        is com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Waiting -> "${status.reason} · $progress"
        is com.xlntsmmr.xlntgallery.data.gemini.GeminiStatus.Error -> "오류: ${status.message}"
    }
}

// 공통 설정 섹션
@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        Text(
            text = title,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = description,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = Color.Gray
        )

        content()
    }
}


@Composable
private fun SelectHardwareChip(
    imageVector: ImageVector,
    title: String,
    content: String,
    selected: Boolean,
    recommended: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected) 3.dp else 0.dp,
                shape = shape
            )
            .clip(shape)
            .background(
                if (selected) Color.White
                else Color.Transparent
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .defaultMinSize(minHeight = 88.dp)
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // 1. 왼쪽 원형 아이콘
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (selected)
                        Color(0xFFD9E3FF)
                    else
                        Color(0xFFE3E6EA),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (selected)
                    MainBlue
                else
                    Color(0xFF454B57),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 2. 제목 + 설명
        Column(
            modifier = Modifier.weight(1f),
//            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF191C20)
                )

                // 자동 항목에만 추천 배지 표시
                if (recommended) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFDCE6FF),
                                RoundedCornerShape(24)
                            )
                            .padding(
                                horizontal = 10.dp,
                            )
                    ) {
                        Text(
                            text = "추천",
                            color = MainBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Text(
                text = content,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = Color(0xFF555C69),
                style = LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 3. 오른쪽 선택 표시
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (selected)
                        MainBlue
                    else
                        Color(0xFFDFE3E8),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}


// 공통 스위치 항목
@Composable
private fun SettingSwitch(
    title: String,
    content: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),

        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF191C20)
            )

            Text(
                text = content,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = Color(0xFF555C69),
                style = LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ))

        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
