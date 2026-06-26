package com.mymusicplayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mymusicplayer.ui.components.MiniPlayerBar
import com.mymusicplayer.ui.navigation.NavGraph
import com.mymusicplayer.ui.navigation.Screen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.NowPlaying.route) {
                MiniPlayerBar(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.systemGestures)
                                .only(WindowInsetsSides.Bottom)
                        )
                        .padding(bottom = 4.dp),
                    onOpenPlayer = { navController.navigate(Screen.NowPlaying.route) }
                )
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
