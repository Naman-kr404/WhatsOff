package com.example.whatsoff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.whatsoff.navigation.AppNavigation
import com.example.whatsoff.ui.theme.WhatsOffTheme
import com.example.whatsoff.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            WhatsOffTheme {
                val navController = rememberNavController()
                val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
                AppNavigation(navController = navController, vm = vm)
            }
        }
    }
}