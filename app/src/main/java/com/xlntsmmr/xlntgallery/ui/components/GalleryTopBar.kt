
package com.xlntsmmr.xlntgallery.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopBar(
    title: String,
    showBackButton: Boolean = false,
    showNotification: Boolean = false,
    showShare: Boolean = false,
    showMore: Boolean = false,
    onBackClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },

            // 왼쪽 버튼
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                }
            },

            // 오른쪽 버튼
            actions = {

                if (showNotification) {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = "알림"
                        )
                    }
                }

                if (showShare) {
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "공유"
                        )
                    }
                }

                if (showMore) {
                    IconButton(onClick = onMoreClick) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "더보기"
                        )
                    }
                }
            },

            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color(0xFF171B22),
                navigationIconContentColor = Color(0xFF424956),
                actionIconContentColor = Color(0xFF424956)
            )
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0xFFF0F1F3)
        )
    }
}
