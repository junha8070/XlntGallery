package com.xlntsmmr.xlntgallery.ui.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xlntsmmr.xlntgallery.ui.components.GalleryTopBar
import com.xlntsmmr.xlntgallery.ui.components.display_bottom_right_radius
import com.xlntsmmr.xlntgallery.ui.detail.PhotoDetailScreen
import com.xlntsmmr.xlntgallery.ui.detail.PhotoDetailViewModel
import com.xlntsmmr.xlntgallery.ui.home.HomeScreen
import com.xlntsmmr.xlntgallery.ui.settings.SettingsScreen

// 디자인 색상
private val SelectedBlue = Color(0xFF005AC2)
private val UnselectedGray = Color(0xFF424956)

// 메뉴 정보
private data class BottomNavItem(
    val route: String, val title: String, val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("home", "갤러리", Icons.Outlined.Collections),
    BottomNavItem("contents", "콘텐츠", Icons.Outlined.TripOrigin),
    BottomNavItem("settings", "설정", Icons.Outlined.Settings)
)


@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute =
        navBackStackEntry?.destination?.route ?: "home"

    // 상세 화면 경로는 "detail/{photoId}" 형태라 앞부분으로 판단
    val isDetail = currentRoute.startsWith("detail")

    // 전체 화면을 겹쳐서 배치할 최상위 Box
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // 1. 화면 콘텐츠와 상단 툴바
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,

            // bottomBar는 사용하지 않음!

            topBar = {
                when {
                    isDetail -> {
                        GalleryTopBar(
                            title = "사진 상세",
                            showBackButton = true,
                            showShare = true,
                            showMore = true,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    currentRoute == "contents" -> {
                        GalleryTopBar(
                            title = "콘텐츠",
                            showNotification = true
                        )
                    }

                    currentRoute == "settings" -> {
                        GalleryTopBar(
                            title = "설정",
                            showNotification = true
                        )
                    }
                }
            }

        ) { innerPadding ->

            // 2. NavHost는 하단바 영역을 제외하지 않음
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding()
                    )
            ) {

                composable("home") {
                    HomeScreen(
                        onPhotoClick = { photo ->
                            navController.navigate("detail/${photo.id}")
                        }
                    )
                }

                composable("contents") {
                    SampleScreen("콘텐츠")
                }

                composable("settings") {
                    SettingsScreen()
                }

                // {photoId} 자리에 사진 ID가 들어옴 → PhotoDetailViewModel의 SavedStateHandle로 전달
                composable(
                    route = "detail/{${PhotoDetailViewModel.ARG_PHOTO_ID}}",
                    arguments = listOf(
                        navArgument(PhotoDetailViewModel.ARG_PHOTO_ID) { type = NavType.LongType }
                    )
                ) {
                    PhotoDetailScreen()
                }
            }
        }

        // 3. 하단 Island를 콘텐츠 위에 띄움
        if (!isDetail) {

            Box(
                modifier = Modifier.align(
                    Alignment.BottomCenter
                )
            ) {
                GalleryBottomBar(
                    selectedRoute = currentRoute,
                    onTabClick = { route ->

                        navController.navigate(route) {
                            popUpTo(
                                navController.graph
                                    .findStartDestination().id
                            ) {
                                saveState = true
                            }

                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}


// 커스텀 하단 네비게이션
@Composable
private fun GalleryBottomBar(
    selectedRoute: String?,
    onTabClick: (String) -> Unit
) {
    val density = LocalDensity.current

    val bottomRightDp = with(density) {
        if (display_bottom_right_radius > 0)
            display_bottom_right_radius.toDp()
        else 32.dp
    }
//    val bottomRightDp = 32.dp

    val cornerShape = AbsoluteRoundedCornerShape(
        topLeft = bottomRightDp,
        topRight = bottomRightDp,
        bottomLeft = bottomRightDp,
        bottomRight = bottomRightDp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = 6.dp
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = cornerShape,
            color = Color.White,
            shadowElevation = 5.dp,
            tonalElevation = 0.dp
        ) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                bottomNavItems.forEach { item ->

                    val isSelected = selectedRoute == item.route

                    val itemColor = if (isSelected) {
                        SelectedBlue
                    } else {
                        UnselectedGray
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onTabClick(item.route) },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null, // 클릭 효과 제거
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            color = itemColor,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

// 임시 화면
@Composable
private fun SampleScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}

@Preview(showBackground = true)
@Composable
private fun GalleryBottomBarPreview() {
    GalleryBottomBar(
        selectedRoute = "contents", onTabClick = {})
}
