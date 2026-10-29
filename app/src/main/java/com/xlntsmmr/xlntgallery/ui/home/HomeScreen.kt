
package com.xlntsmmr.xlntgallery.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.xlntsmmr.xlntgallery.ui.permission.hasPhotoPermission
import com.xlntsmmr.xlntgallery.ui.permission.photoPermissions

// ① 전체 홈 화면
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {

    // 검색창에 입력된 내용을 기억하는 객체
    val searchState = rememberTextFieldState()
    val context = LocalContext.current

    // 검색 모드 여부 (true면 검색창이 위로 올라가고 결과 영역이 보임)
    // rememberSaveable: 화면 회전·다크모드 전환에도 값 유지
    var isSearching by rememberSaveable { mutableStateOf(false) }

    // 포커스(=키보드) 제어용
    val focusManager = LocalFocusManager.current

    // 검색 실행: 키보드만 내리고 검색 모드는 유지 (지금은 테스트용 토스트)
    val runSearch = {
        val query = searchState.text.toString().trim()
        focusManager.clearFocus()

        if (query.isNotEmpty()) {
            showToast(context = context, message = "검색어: $query")
        }
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
        } else if (!permissionRequested) {
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
                // TODO: 다음 단계에서 SearchResults(fakeSearchResults)로 교체
                SearchFilterRow(
                    selectedFilters = selectedFilters,
                    onToggle = { type ->
                        selectedFilters =
                            if (type in selectedFilters) selectedFilters - type
                            else selectedFilters + type
                    }
                )

                Text(
                    text = "검색 결과 영역",
                    fontSize = 13.sp,
                    color = Color(0xFF9499A5)
                )


                PhotoGrid(photos)

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
    photos: List<Photo>,                 // ← PhotoSearchResult → Photo
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        // bottom 여백: 플로팅 하단바에 마지막 줄이 가려지지 않게
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = photos,
            key = { it.id }              // ← it.photo.id → it.id
        ) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE5E7EB))   // 로딩 중에 보이는 회색 배경
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 700
)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
