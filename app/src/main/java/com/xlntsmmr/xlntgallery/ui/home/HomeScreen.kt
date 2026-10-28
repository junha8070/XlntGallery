
package com.xlntsmmr.xlntgallery.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xlntsmmr.xlntgallery.ui.components.ShowToast

private val MainBlue = Color(0xFF005AC2)

// ① 전체 홈 화면
@Composable
fun HomeScreen() {

    // 검색창에 입력된 내용을 기억하는 객체
    val searchState = rememberTextFieldState()
    val context = LocalContext.current

    // 검색 실행 (지금은 테스트용)
    val runSearch = {
        val query = searchState.text.toString().trim()

        if (query.isNotEmpty()) {
            ShowToast(context = context, message = "검색어: $query")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 안내 영역
            HomeIntro()

            Spacer(modifier = Modifier.height(24.dp))

            // 검색창
            GallerySearchBar(
                state = searchState,
                onSearch = runSearch,
                onVoiceClick = {
                    ShowToast(context, "음석 검색")
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 추천 검색어
            SearchSuggestions(
                onClick = { keyword ->
                    searchState.setTextAndPlaceCursorAtEnd(keyword)
                }
            )
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
    onSearch: () -> Unit,
    onVoiceClick: () -> Unit
) {

    BasicTextField(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .shadow(6.dp, CircleShape)
            .background(Color.White, CircleShape),

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
    )
}

// 추천 검색어 데이터
private data class SearchSuggestion(
    val title: String,
    val icon: ImageVector
)

private val suggestions = listOf(
    SearchSuggestion("신분증", Icons.Outlined.Badge),
    SearchSuggestion("영수증", Icons.Outlined.ReceiptLong),
    SearchSuggestion("강아지", Icons.Outlined.Pets),
    SearchSuggestion("작년 여행", Icons.Outlined.Sailing),
    SearchSuggestion("계약서", Icons.Outlined.Description)
)

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

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 700
)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
