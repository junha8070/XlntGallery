package com.xlntsmmr.xlntgallery.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xlntsmmr.xlntgallery.ui.components.GalleryTopBar

import com.xlntsmmr.xlntgallery.ui.home.HomeScreen

// 디자인 색상
private val SelectedBlue = Color(0xFF005AC2)
private val UnselectedGray = Color(0xFF424956)

// 메뉴 정보
private data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("home", "홈", Icons.Outlined.Collections),
    BottomNavItem("search", "검색", Icons.Outlined.Search),
    BottomNavItem("settings", "설정", Icons.Outlined.Settings)
)


@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute =
        navBackStackEntry?.destination?.route ?: "home"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,

        // 공통 상단 툴바
        topBar = {
            when (currentRoute) {

                "home", "search" -> {
                    GalleryTopBar(
                        title = "Xlnt Gallery",
                        showNotification = true
                    )
                }

                "settings" -> {
                    GalleryTopBar(
                        title = "설정",
                        showNotification = true
                    )
                }

                "detail" -> {
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
            }
        },

        // 공통 하단 네비게이션
        bottomBar = {

            // 사진 상세에서는 하단바 숨김
            if (currentRoute != "detail") {

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

    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            composable("home") {
                HomeScreen()
            }

            composable("search") {
                SampleScreen("검색 화면")
            }

            composable("settings") {
                SampleScreen("설정 화면")
            }

            composable("detail") {
                SampleScreen("사진 상세 화면")
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 12.dp
            )
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            color = Color.White,
            shadowElevation = 14.dp,
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
                                onClick = { onTabClick(item.route) }
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}

@Preview(showBackground = true)
@Composable
private fun GalleryBottomBarPreview() {
    GalleryBottomBar(
        selectedRoute = "search",
        onTabClick = {}
    )
}
