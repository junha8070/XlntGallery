
package com.xlntsmmr.xlntgallery.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import com.xlntsmmr.xlntgallery.data.search.MatchType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.xlntsmmr.xlntgallery.data.Photo
import com.xlntsmmr.xlntgallery.ui.components.MainBlue
import com.xlntsmmr.xlntgallery.ui.components.showToast
import com.xlntsmmr.xlntgallery.ui.permission.hasMediaLocationPermission
import com.xlntsmmr.xlntgallery.ui.permission.hasPhotoPermission
import com.xlntsmmr.xlntgallery.ui.permission.photoPermissions

// ① 전체 홈 화면
@Composable
fun HomeScreen(
    onPhotoClick: (Photo) -> Unit,   // 사진을 눌렀을 때 (어디로 이동할지는 내비게이션이 결정)
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {

    // 검색창에 입력된 내용을 기억하는 객체
    val searchState = rememberTextFieldState()
    val context = LocalContext.current

    // 검색 모드 여부 (true면 검색창이 위로 올라가고 결과 영역이 보임)
    // rememberSaveable: 화면 회전·다크모드 전환에도 값 유지
    var isSearching by rememberSaveable { mutableStateOf(false) }

    // 포커스(=키보드) 제어용
    val focusManager = LocalFocusManager.current

    // 검색 실행: 검색은 입력하는 동안 자동으로 되므로, 엔터는 키보드만 내림
    val runSearch = {
        focusManager.clearFocus()
    }

    var selectedFilters by rememberSaveable {
        mutableStateOf(setOf(SearchFilterType.FILE_NAME, SearchFilterType.OCR, SearchFilterType.AI))
    }

    // 검색 모드 종료: 상태 리셋 + 포커스 해제(키보드도 같이 내려감)
    val exitSearch = {
        isSearching = false
        searchState.clearText()
        focusManager.clearFocus()
    }

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchState.collectAsStateWithLifecycle()
    val indexStatus by viewModel.indexStatus.collectAsStateWithLifecycle()

    // 검색창 글자가 바뀔 때마다 ViewModel에 전달 (snapshotFlow: Compose 상태 → Flow)
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { viewModel.onQueryChange(it) }
    }
    // 필터 칩 → 검색 방식
    LaunchedEffect(selectedFilters) {
        viewModel.setFilters(
            selectedFilters.map { filter ->
                when (filter) {
                    SearchFilterType.FILE_NAME -> MatchType.FILE_NAME
                    SearchFilterType.OCR -> MatchType.OCR
                    SearchFilterType.AI -> MatchType.AI
                }
            }.toSet()
        )
    }

    // ── 사진 권한 ─────────────────────────────────────
    // 권한 요청 창을 띄우고, 사용자가 응답하면 아래 콜백이 호출됨
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // 결과 Map 대신 직접 다시 확인: "일부 사진만 허용"도 허용으로 처리하기 위해
        if (hasPhotoPermission(context)) {
            viewModel.loadPhotos()
        } else {
            showToast(context, "사진 권한이 없으면 사진을 검색할 수 없어요")
        }
    }

    // 이미 한 번 물어봤는지 (회전할 때마다 요청 창이 다시 뜨는 것 방지)
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    // 화면이 처음 나타날 때 1번 실행
    LaunchedEffect(Unit) {
        if (hasPhotoPermission(context)) {
            viewModel.loadPhotos()                    // 이미 허용됨 → 바로 불러오기
        }
        // 사진 권한이 없거나, 나중에 추가된 위치 권한(ACCESS_MEDIA_LOCATION)이 아직 없으면 요청
        if (!permissionRequested && (!hasPhotoPermission(context) || !hasMediaLocationPermission(context))) {
            permissionRequested = true
            permissionLauncher.launch(photoPermissions())   // 요청 창 띄우기
        }
    }

    // 검색 중일 때만 뒤로가기를 가로챔 (enabled가 없으면 앱 종료가 막히는 버그 발생)
    BackHandler(enabled = isSearching) {
        exitSearch()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        // maxHeight = 이 화면의 실제 높이. 기기마다 달라서 고정 dp 대신 비율로 계산
        val topPadding by animateDpAsState(
            targetValue = if (isSearching) 8.dp else maxHeight * 0.22f,
            animationSpec = tween(durationMillis = 300),
            label = "searchBarTop"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 안내 영역: 검색 중이면 접히면서 사라짐
            AnimatedVisibility(visible = !isSearching) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomeIntro()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // 검색창
            GallerySearchBar(
                state = searchState,
                isSearching = isSearching,
                onFocused = { isSearching = true },
                onSearch = runSearch,
                onVoiceClick = {
                    showToast(context, "음성 검색")
                },
                onClose = exitSearch
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isSearching) {
                SearchFilterRow(
                    selectedFilters = selectedFilters,
                    onToggle = { type ->
                        selectedFilters =
                            if (type in selectedFilters) selectedFilters - type
                            else selectedFilters + type
                    }
                )

                IndexStatusBar(
                    status = indexStatus,
                    aiFilterOn = SearchFilterType.AI in selectedFilters
                )

                when (val result = searchResult) {
                    // 검색어가 없으면 전체 사진
                    SearchUiState.Idle -> PhotoGrid(
                        photos = photos,
                        onPhotoClick = onPhotoClick,
                        modifier = Modifier.weight(1f)
                    )

                    SearchUiState.Searching -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MainBlue) }

                    is SearchUiState.Results -> {
                        ResultHeader(count = result.hits.size)
                        if (result.hits.isEmpty()) {
                            Text(
                                text = "검색 결과가 없어요",
                                fontSize = 14.sp,
                                color = Color(0xFF9499A5),
                                modifier = Modifier.padding(top = 40.dp)
                            )
                        } else {
                            // 사진 ID → 뱃지 (검색에 걸린 방식)
                            val badges = remember(result) {
                                result.hits.associate { hit ->
                                    hit.photo.id to hit.matches.joinToString(" · ") { it.label }
                                }
                            }
                            PhotoGrid(
                                photos = result.hits.map { it.photo },
                                onPhotoClick = onPhotoClick,
                                badge = { photo -> badges[photo.id] },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

            } else {
                // 추천 검색어: 누르면 바로 검색 모드로
                SearchSuggestions(
                    onClick = { keyword ->
                        searchState.setTextAndPlaceCursorAtEnd(keyword)
                        isSearching = true
                    }
                )
            }
        }
    }
}

// 검색 결과 개수
@Composable
private fun ResultHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("검색 결과", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16181D))
        Spacer(modifier = Modifier.width(6.dp))
        Text("${count}장", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MainBlue)
    }
}

// 사진 분석 진행 상황 (분석이 끝났고 AI 모델도 있으면 숨김)
@Composable
private fun IndexStatusBar(status: IndexStatus, aiFilterOn: Boolean) {
    val download = status.modelDownload
    val message = when {
        status.total == 0 && status.isRunning -> "사진 목록을 불러오는 중…"
        status.isRunning || status.geminiRunning ->
            "사진 분석 중 · 글자 ${status.ocrDone}/${status.total}" +
                (if (status.siglipDone > 0) " · SigLIP ${status.siglipDone}" else "") +
                (if (status.clipDone > 0) " · CLIP ${status.clipDone}" else "") +
                (if (status.geminiTagged > 0 || status.geminiRunning) " · Gemini ${status.geminiTagged}" else "")
        download != null && download >= 0f -> "AI 검색 모델 받는 중 ${(download * 100).toInt()}%"
        download != null -> "Wi-Fi에 연결되면 AI 검색 모델을 받아요 (설정에서 모바일 데이터 허용 가능)"
        aiFilterOn && !status.modelsReady -> "AI 검색 모델을 준비하고 있어요"
        else -> null
    } ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0F3F8))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status.isRunning || status.geminiRunning) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MainBlue)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(message, fontSize = 12.sp, color = Color(0xFF424956))
    }
}

// ② 로봇 아이콘 + 제목 + 설명
@Composable
private fun HomeIntro() {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    color = Color(0xFFF0F3F8),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = MainBlue,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI 사진 검색",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16181D)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MainBlue,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "사진을 더 똑똑하게 찾아보세요",
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )
    }
}

// ③ 검색창
@Composable
private fun GallerySearchBar(
    state: TextFieldState,
    isSearching: Boolean,
    onFocused: () -> Unit,   // 검색창이 포커스를 받으면 호출 → 바깥(HomeScreen)이 상태 변경
    onSearch: () -> Unit,
    onVoiceClick: () -> Unit,
    onClose: () -> Unit,     // X 버튼
    modifier: Modifier = Modifier
) {

    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .shadow(6.dp, CircleShape)
            .background(Color.White, CircleShape)
            .onFocusChanged { focusState ->
                // 검색창을 누르면 포커스가 생김 → 검색 모드 진입을 바깥에 알림
                if (focusState.isFocused) onFocused()
            },

        textStyle = TextStyle(
            fontSize = 14.sp,
            color = Color(0xFF111827)
        ),

        lineLimits = TextFieldLineLimits.SingleLine,

        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),

        onKeyboardAction = {
            onSearch()
        },

        decorator = { innerTextField ->

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 검색 아이콘
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "검색",
                        tint = MainBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 실제 텍스트 입력 영역
                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    if (state.text.isEmpty()) {
                        Text(
                            text = "신분증, 작년 바다 여행, 돈 보낸 사진",
                            fontSize = 13.sp,
                            color = Color(0xFF9499A5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    innerTextField()
                }

                if (isSearching) {
                    // 검색 중: 닫기(X) 버튼
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "검색 닫기",
                            tint = Color(0xFF424956),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // 마이크
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "음성 검색",
                            tint = Color(0xFF424956),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    )
}

// 추천 검색어 데이터
private data class SearchSuggestion(
    val title: String,
    val icon: ImageVector
)

data class PhotoSearchResult(
    val photo: Photo,              // 원본 정보는 통째로 들고 있고
//    val matchType: MatchType,      // 화면용 정보만 추가
)




private val suggestions = listOf(
    SearchSuggestion("신분증", Icons.Outlined.Badge),
    SearchSuggestion("영수증", Icons.Outlined.Receipt),
    SearchSuggestion("강아지", Icons.Outlined.Pets),
    SearchSuggestion("작년 여행", Icons.Outlined.Sailing),
    SearchSuggestion("계약서", Icons.Outlined.Description)
)

private enum class SearchFilterType(val title: String, val icon: ImageVector) {
    FILE_NAME("파일 이름", Icons.Outlined.TextFields),
    OCR("글자 인식", Icons.Outlined.DocumentScanner),
    AI("AI 추론", Icons.Outlined.Psychology)
}

// 필터 줄 전체
@Composable
private fun SearchFilterRow(
    selectedFilters: Set<SearchFilterType>,
    onToggle: (SearchFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchFilterType.entries) { type ->
            FilterChipItem(
                type = type,
                selected = type in selectedFilters,
                onClick = { onToggle(type) }
            )
        }
    }
}

// 칩 1개: 선택 여부만 받아서 그림
@Composable
private fun FilterChipItem(
    type: SearchFilterType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) MainBlue else Color(0xFFF0F3F8)
    val contentColor = if (selected) Color.White else Color(0xFF242934)

    Row(
        modifier = Modifier
            .clip(shape)                       // 1. 모양 자르기
            .background(backgroundColor)       // 2. 배경
            .toggleable(                       // 3. 클릭 (켜기/끄기)
                value = selected,
                role = Role.Checkbox,          // TalkBack이 "체크박스, 선택됨"이라고 읽어줌
                onValueChange = { onClick() }
            )
            .padding(horizontal = 14.dp, vertical = 5.dp),  // 4. 안쪽 여백
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            // 디자인처럼 선택되면 체크 아이콘으로
            imageVector = if (selected) Icons.Outlined.Check else type.icon,
            contentDescription = null,
            tint = if (selected) Color.White else MainBlue,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = type.title,
            fontSize = 12.sp,
            color = contentColor,
            maxLines = 1
        )
    }
}

// ④ 추천 검색어 목록
@Composable
private fun SearchSuggestions(
    onClick: (String) -> Unit
) {

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        items(suggestions) { suggestion ->

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFF0F1F5),
                        shape = CircleShape
                    )
                    .clickable {
                        onClick(suggestion.title)
                    }
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = suggestion.icon,
                    contentDescription = null,
                    tint = MainBlue,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = suggestion.title,
                    fontSize = 12.sp,
                    color = Color(0xFF242934),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    badge: (Photo) -> String? = { null }   // 사진 위에 표시할 글자 (검색 결과: "글자 인식" 등)
) {
    BoxWithConstraints(modifier = modifier) {

        // 1. 화면 너비로 기본 열 수 계산 (사진 한 칸 최소 180dp 기준)
        //    폰(약 400dp) → 2열, 폴더블 펼침(약 700dp) → 3열, 태블릿 → 4~6열
        val defaultColumns = (maxWidth / 180.dp).toInt().coerceIn(2, 6)

        // 2. 현재 열 수. 접고 펼쳐서 defaultColumns가 바뀌면 그 값으로 다시 시작
        var columns by rememberSaveable(defaultColumns) {
            mutableIntStateOf(defaultColumns)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(
                    onZoomIn = { columns = (columns - 1).coerceAtLeast(1) },  // 벌리기 → 크게
                    onZoomOut = { columns = (columns + 1).coerceAtMost(8) }   // 오므리기 → 작게
                ),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = photos, key = { it.id }) { photo ->
                Box(
                    modifier = Modifier
                        .animateItem()          // 열 수가 바뀔 때 사진이 부드럽게 이동
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE5E7EB))
                        .clickable { onPhotoClick(photo) }
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 열이 많아 사진이 작으면 뱃지는 생략
                    val label = if (columns <= 3) badge(photo) else null
                    if (label != null) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MainBlue.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// 두 손가락 핀치만 감지하는 Modifier (한 손가락 스크롤은 그대로 통과)
private fun Modifier.pinchToChangeColumns(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var accumulatedZoom = 1f

        do {
            // Initial 패스: 그리드의 스크롤보다 "먼저" 이벤트를 받음
            val event = awaitPointerEvent(PointerEventPass.Initial)

            if (event.changes.size >= 2) {                 // 손가락 2개 이상일 때만
                accumulatedZoom *= event.calculateZoom()

                if (accumulatedZoom > 1.25f) {             // 25% 이상 벌림
                    onZoomIn()
                    accumulatedZoom = 1f
                } else if (accumulatedZoom < 0.8f) {       // 20% 이상 오므림
                    onZoomOut()
                    accumulatedZoom = 1f
                }
                // 핀치 중엔 이벤트를 소비 → 그리드가 스크롤하지 않음
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 700
)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(onPhotoClick = {})
}
