package com.xlntsmmr.xlntgallery.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
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
import com.xlntsmmr.xlntgallery.ui.components.MainBlue

@Composable
fun SettingsScreen() {

    // 설정값을 기억하는 상태
    var selectedHardware by rememberSaveable {
        mutableStateOf("AUTO")
    }

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
                        selectedHardware = "AUTO"
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.Memory,
                    title = "NPU 우선",
                    content = "Galaxy 온디바이스 NPU 가속 엔진 사용",
                    selected = selectedHardware == "NPU",
                    onClick = {
                        selectedHardware = "NPU"
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.Speed,
                    title = "GPU 우선",
                    content = "고속 그래픽 병렬 처리 가속",
                    selected = selectedHardware == "GPU",
                    onClick = {
                        selectedHardware = "GPU"
                    }
                )

                SelectHardwareChip(
                    imageVector = Icons.Outlined.DeveloperBoard,
                    title = "CPU",
                    content = "표준 연산 유닛 기반 안정 모드",
                    selected = selectedHardware == "CPU",
                    onClick = {
                        selectedHardware = "CPU"
                    }
                )
            }
        }


        // ② 검색 정확도
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
                    title = "충전 중 분석",
                    content = "충전 중일 때 자동으로 사진을 분석해요.",
                    checked = privacyFilterEnabled,
                    onCheckedChange = {
                        privacyFilterEnabled = it
                    }
                )

                SettingSwitch(
                    title = "새 사진 자동 분석",
                    content = "새로 촬영하거나 저장한 사진을 분석해요.",
                    checked = privacyFilterEnabled,
                    onCheckedChange = {
                        privacyFilterEnabled = it
                    }
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
