package com.example.whatsoff.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.whatsoff.model.ConnectionState
import com.example.whatsoff.ui.ChatScreen
import com.example.whatsoff.ui.ConnectionScreen
import com.example.whatsoff.viewmodel.ChatViewModel

object Screen {
    const val CONNECTION = "connection"
    const val CHAT = "chat"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    vm: ChatViewModel
) {
    val connectionState by vm.connectionState.collectAsState()

    // Reactively navigate to ChatScreen once peer connection is established
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            if (navController.currentDestination?.route != Screen.CHAT) {
                navController.navigate(Screen.CHAT) {
                    popUpTo(Screen.CONNECTION) { saveState = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.CONNECTION
    ) {
        composable(Screen.CONNECTION) {
            ConnectionScreen(vm = vm)
        }

        composable(Screen.CHAT) {
            ChatScreen(
                vm = vm,
                onNavigateBack = {
                    if (!navController.popBackStack(Screen.CONNECTION, inclusive = false)) {
                        navController.navigate(Screen.CONNECTION)
                    }
                }

            )
        }
    }
}