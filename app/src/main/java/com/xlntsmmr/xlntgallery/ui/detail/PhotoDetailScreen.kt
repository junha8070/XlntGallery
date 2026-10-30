package com.xlntsmmr.xlntgallery.ui.detail

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.xlntsmmr.xlntgallery.data.OcrBox
import com.xlntsmmr.xlntgallery.data.OcrLine
import com.xlntsmmr.xlntgallery.data.PhotoDetail
import com.xlntsmmr.xlntgallery.ui.components.MainBlue
import com.xlntsmmr.xlntgallery.ui.components.copyToClipboard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val ScreenBackground = Color(0xFFF6F7F9)
private val SectionBackground = Color(0xFFEEF0F4)
private val TextPrimary = Color(0xFF16181D)
private val TextSecondary = Color(0xFF6B7280)
private val HighlightColor = Color(0xFFFFB300)   // 선택한 항목의 위치 강조색

private val SheetPeekHeight = 120.dp             // 접혀 있을 때 보이는 시트 높이

/** 바깥: ViewModel에서 상태를 꺼내서 안쪽에 넘겨주기만 함 */
@Composable
fun PhotoDetailScreen(
    viewModel: PhotoDetailViewModel = viewModel(factory = PhotoDetailViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PhotoDetailContent(uiState = uiState)
}

/** 안쪽: 상태만 받아서 그림 (ViewModel 없이 Preview 가능) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDetailContent(uiState: PhotoDetailUiState) {
    val detail = uiState.detail

    if (uiState.isLoading || detail == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(ScreenBackground),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) CircularProgressIndicator(color = MainBlue)
            else Text("사진을 찾을 수 없어요", color = TextSecondary)
        }
        return
    }

    val sheetState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    // 사진 위 글자 영역 표시 on/off
    var showTextBoxes by rememberSaveable { mutableStateOf(true) }
    // 사용자가 누른 항목 → 사진에서 그 위치를 강조
    var selectedInfo by remember { mutableStateOf<CopyableInfo?>(null) }

    val ocrState = uiState.ocr
    val ocrLines = (ocrState as? OcrState.Done)?.lines.orEmpty()
    val words = (ocrState as? OcrState.Done)?.words.orEmpty()

    // 사진에서 길게 눌러 선택한 단어 범위 (읽는 순서 인덱스). 인식 결과가 바뀌면 초기화
    var wordSelection by remember(words) { mutableStateOf<IntRange?>(null) }

    // 화면 아래 시스템 내비게이션 바 높이 (목록 마지막이 가려지지 않게)
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 펼쳤을 때 시트 높이. 내용 길이와 상관없이 "고정" → 목록이 그려지는 동안 시트 높이가
        // 바뀌며 덜컹거리지 않고, 시트 안 스크롤과 시트 끌기가 자연스럽게 이어짐
        val sheetHeight = maxHeight * 0.8f

        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = SheetPeekHeight,
            sheetContainerColor = Color.White,
            containerColor = ScreenBackground,
            sheetContent = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + navigationBarHeight
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OcrSection(
                            ocr = ocrState,
                            selectedInfo = selectedInfo,
                            onSelect = { info ->
                                // 같은 항목을 다시 누르면 선택 해제
                                selectedInfo = if (selectedInfo == info) null else info
                                showTextBoxes = true
                                // 시트를 내려서 사진에서 위치가 보이게
                                scope.launch { sheetState.bottomSheetState.partialExpand() }
                            }
                        )
                    }
                    item { MetadataSection(detail) }
                }
            }
        ) { innerPadding ->
            // innerPadding.bottom = 접힌 시트 높이 → 사진이 시트에 가려지지 않음
            PhotoArea(
                detail = detail,
                lines = ocrLines,
                showTextBoxes = showTextBoxes,
                onToggleTextBoxes = { showTextBoxes = !showTextBoxes },
                selectedBoxes = selectedInfo?.boxes.orEmpty(),
                words = words,
                wordSelection = wordSelection,
                onWordSelectionChange = { wordSelection = it },
                // 사진 위 아무 데서나 위로 쓸면 시트 펼치기, 아래로 쓸면 접기
                onSwipeUp = { scope.launch { sheetState.bottomSheetState.expand() } },
                onSwipeDown = { scope.launch { sheetState.bottomSheetState.partialExpand() } },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

// ① 사진 영역: 남은 공간에 사진을 최대한 크게 + 글자 영역 표시
@Composable
private fun PhotoArea(
    detail: PhotoDetail,
    lines: List<OcrLine>,
    showTextBoxes: Boolean,
    onToggleTextBoxes: () -> Unit,
    selectedBoxes: List<OcrBox>,
    words: List<SelectableWord>,
    wordSelection: IntRange?,
    onWordSelectionChange: (IntRange?) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    // pointerInput(Unit)은 처음 한 번만 만들어지므로, 최신 콜백을 참조하도록 감쌈
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentWords by rememberUpdatedState(words)
    val currentOnWordSelectionChange by rememberUpdatedState(onWordSelectionChange)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // 사진의 실제(회전 반영된) 비율
    val ratio = if (detail.width > 0 && detail.height > 0) {
        detail.width.toFloat() / detail.height
    } else 1f

    Box(
        modifier = modifier.pointerInput(Unit) {
            val threshold = 40.dp.toPx()   // 이만큼 이상 움직여야 스와이프로 인정
            var totalDrag = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDrag = 0f },
                onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                onDragEnd = {
                    when {
                        totalDrag < -threshold -> currentOnSwipeUp()    // 위로 (y가 줄어듦)
                        totalDrag > threshold -> currentOnSwipeDown()   // 아래로
                    }
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {

        // aspectRatio: 주어진 공간 안에서 이 비율로 들어가는 가장 큰 크기를 차지
        // → 사진과 박스(Canvas)가 정확히 같은 크기·위치가 되어 좌표가 딱 맞음
        Box(
            modifier = Modifier
                .aspectRatio(ratio)
                // 짧게 탭: 선택 해제. (onLongPress를 비워 두지 않으면 길게 눌렀다 뗄 때도 탭으로 처리됨)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { /* 선택은 아래 제스처가 처리 */ },
                        onTap = { currentOnWordSelectionChange(null) }
                    )
                }
                // 길게 누르기 → 그 단어 선택 시작, 그대로 끌면 범위 확장
                .pointerInput(Unit) {
                    var anchor = -1
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val index = findWord(
                                currentWords, offset.x / size.width, offset.y / size.height, strict = true
                            )
                            anchor = index ?: -1
                            if (index != null) {
                                currentOnWordSelectionChange(index..index)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)   // 진동
                            }
                        },
                        onDrag = { change, _ ->
                            if (anchor >= 0) {
                                val index = findWord(
                                    currentWords,
                                    change.position.x / size.width,
                                    change.position.y / size.height,
                                    strict = false
                                )
                                if (index != null) {
                                    currentOnWordSelectionChange(min(anchor, index)..max(anchor, index))
                                }
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = detail.uri,
                contentDescription = detail.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if ((showTextBoxes && lines.isNotEmpty()) || wordSelection != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (!showTextBoxes) {
                        // 글자 영역 숨김 상태에서도 선택한 단어는 보여줌
                        wordSelection?.forEach { i ->
                            words.getOrNull(i)?.let { drawOcrBox(it.box, SelectionFill, MainBlue, 2f) }
                        }
                        return@Canvas
                    }
                    // 인식된 모든 줄: 옅은 파란 박스
                    lines.forEach { line ->
                        drawOcrBox(line.box, fill = MainBlue.copy(alpha = 0.15f), stroke = MainBlue.copy(alpha = 0.8f))
                    }
                    // 선택한 항목: 진한 노란 박스
                    selectedBoxes.forEach { box ->
                        drawOcrBox(box, fill = HighlightColor.copy(alpha = 0.35f), stroke = HighlightColor, strokeWidth = 2.5f)
                    }
                    // 길게 눌러 선택한 단어: 진한 파란 박스
                    wordSelection?.forEach { i ->
                        words.getOrNull(i)?.let { drawOcrBox(it.box, SelectionFill, MainBlue, 2f) }
                    }
                }
            }
        }

        // 아래: 선택한 글자 + 복사 버튼
        val selectedText = wordSelection?.let { range -> joinSelectedWords(words, range) }
        if (!selectedText.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF016181D))
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedText,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "복사",
                    color = Color(0xFF8AB4FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { copyToClipboard(context, "선택한 글자", selectedText) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "선택 해제",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onWordSelectionChange(null) }
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        }

        // 오른쪽 위: 글자 영역 표시 켜기/끄기
        if (lines.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (showTextBoxes) MainBlue else Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onToggleTextBoxes)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DocumentScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (showTextBoxes) "글자 영역 숨기기" else "글자 영역 보기",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

private val SelectionFill = Color(0x66005AC2)

/**
 * 눌린 위치(비율 좌표)에 있는 단어 찾기.
 * strict = true: 단어 위나 아주 가까이 눌렀을 때만 (길게 눌러 시작할 때)
 * strict = false: 가장 가까운 단어 (끌어서 범위를 넓힐 때)
 */
private fun findWord(words: List<SelectableWord>, x: Float, y: Float, strict: Boolean): Int? {
    if (words.isEmpty()) return null
    var bestIndex = -1
    var bestDistance = Float.MAX_VALUE
    words.forEachIndexed { index, word ->
        // 박스 밖이면 박스까지의 거리, 안이면 0
        val dx = max(0f, max(word.box.left - x, x - word.box.right))
        val dy = max(0f, max(word.box.top - y, y - word.box.bottom))
        val distance = dx * dx + dy * dy
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    val nearEnough = bestDistance < 0.03f * 0.03f
    return if (!strict || nearEnough) bestIndex else null
}

// 선택된 단어들을 글자로: 같은 줄은 띄어쓰기, 줄이 바뀌면 줄바꿈
private fun joinSelectedWords(words: List<SelectableWord>, range: IntRange): String {
    val builder = StringBuilder()
    var previousLine = -1
    for (i in range) {
        val word = words.getOrNull(i) ?: continue
        if (previousLine != -1) builder.append(if (word.lineIndex != previousLine) "\n" else " ")
        builder.append(word.text)
        previousLine = word.lineIndex
    }
    return builder.toString()
}

// 비율(0~1) 좌표 → 실제 픽셀로 바꿔서 둥근 사각형 그리기
private fun DrawScope.drawOcrBox(
    box: OcrBox,
    fill: Color,
    stroke: Color,
    strokeWidth: Float = 1.5f
) {
    val topLeft = Offset(box.left * size.width, box.top * size.height)
    val boxSize = Size((box.right - box.left) * size.width, (box.bottom - box.top) * size.height)
    val radius = CornerRadius(3.dp.toPx())

    drawRoundRect(color = fill, topLeft = topLeft, size = boxSize, cornerRadius = radius)
    drawRoundRect(
        color = stroke,
        topLeft = topLeft,
        size = boxSize,
        cornerRadius = radius,
        style = Stroke(width = strokeWidth.dp.toPx())
    )
}

// ② OCR로 찾은 복사용 항목
@Composable
private fun OcrSection(
    ocr: OcrState,
    selectedInfo: CopyableInfo?,
    onSelect: (CopyableInfo) -> Unit
) {
    val context = LocalContext.current
    val items = (ocr as? OcrState.Done)?.items.orEmpty()

    SectionCard(
        icon = Icons.Outlined.DocumentScanner,
        title = "추출된 정보",
        count = items.size.takeIf { it > 0 },
        onCopyAll = if (items.isEmpty()) null else {
            {
                // 항목들을 "이름: 값" 형식으로 한 번에
                val text = items.joinToString("\n") { "${it.label}: ${it.value}" }
                copyToClipboard(context, "추출된 정보", text, isSensitive = items.any { it.isSensitive })
            }
        }
    ) {
        when (ocr) {
            OcrState.Loading -> Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MainBlue
                )
                Spacer(Modifier.width(10.dp))
                Text("사진 속 글자를 읽는 중…", fontSize = 14.sp, color = TextSecondary)
            }

            is OcrState.Failed -> EmptyText("글자를 인식하지 못했어요 (${ocr.message})")

            is OcrState.Done -> {
                Text(
                    text = if (ocr.usedFallback) "PaddleOCR 모델이 없어 ML Kit으로 읽었어요 (설정에서 다운로드)"
                    else "인식 엔진: ${ocr.engineName}",
                    fontSize = 12.sp,
                    color = if (ocr.usedFallback) HighlightColor else TextSecondary
                )
                if (ocr.items.isEmpty()) {
                    EmptyText("사진에서 글자를 찾지 못했어요")
                } else {
                    if (ocr.isRefining) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MainBlue
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("글자 영역을 확대해서 정확도를 높이는 중…", fontSize = 12.sp, color = MainBlue)
                        }
                    } else {
                        Text(
                            text = "항목을 누르면 사진에서 위치를 보여줘요. 사진의 글자를 길게 눌러 원하는 부분만 복사할 수도 있어요",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    ocr.items.forEach { info ->
                        InfoRow(
                            info = info,
                            selected = info == selectedInfo,
                            onClick = if (info.boxes.isNotEmpty()) ({ onSelect(info) }) else null
                        )
                    }
                }
            }
        }
    }
}

// ③ 사진 메타데이터 (촬영 일시, 위치 등)
@Composable
private fun MetadataSection(detail: PhotoDetail) {
    val context = LocalContext.current

    // 값이 있는 것만 항목으로 만듦
    val items = buildList {
        add(CopyableInfo("촬영 일시", formatDate(detail.dateTaken, "yyyy. MM. dd a h:mm")))
        detail.address?.let { add(CopyableInfo("위치", it)) }
        if (detail.latitude != null && detail.longitude != null) {
            add(CopyableInfo("좌표", "%.6f, %.6f".format(Locale.US, detail.latitude, detail.longitude)))
        }
        detail.camera?.let { add(CopyableInfo("카메라", it)) }
        add(
            CopyableInfo(
                "크기",
                "${detail.width}×${detail.height} · ${Formatter.formatShortFileSize(context, detail.sizeBytes)}"
            )
        )
        add(CopyableInfo("파일 이름", detail.name))
    }

    SectionCard(
        icon = Icons.Outlined.Info,
        title = "사진 정보",
        count = null,
        onCopyAll = {
            copyToClipboard(context, "사진 정보", items.joinToString("\n") { "${it.label}: ${it.value}" })
        }
    ) {
        items.forEach { info -> InfoRow(info = info) }
        if (detail.latitude == null) {
            EmptyText("위치 정보가 없는 사진이에요")
        }
    }
}

// ── 공통 컴포넌트 ─────────────────────────────────────

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    count: Int?,
    onCopyAll: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SectionBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MainBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${count}개 항목",
                    fontSize = 12.sp,
                    color = MainBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDDE7F7))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            if (onCopyAll != null) {
                Text(
                    text = "전체 복사",
                    fontSize = 13.sp,
                    color = MainBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable(onClick = onCopyAll)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        content()
    }
}

@Composable
private fun InfoRow(
    info: CopyableInfo,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null      // null이면 눌러도 반응 없음 (위치 정보 없는 항목)
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0xFFFFF4D6) else Color.White)   // 선택되면 노란 배경
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(info.label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = info.displayValue,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 6        // "전체 텍스트"가 너무 길면 화면을 다 차지하지 않게
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEEF0F4))
                .clickable {
                    copyToClipboard(context, info.label, info.value, info.isSensitive)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = Color(0xFF424956),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("복사", fontSize = 13.sp, color = Color(0xFF424956))
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
}

private fun formatDate(millis: Long, pattern: String): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
