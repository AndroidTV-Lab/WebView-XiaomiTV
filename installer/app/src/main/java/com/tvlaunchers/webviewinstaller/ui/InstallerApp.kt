package com.tvlaunchers.webviewinstaller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.Screen

@Composable
fun InstallerApp(vm: InstallerViewModel, onExit: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) onExit()
    }

    BackHandler(
        enabled = state.dialog == null && state.screen != Screen.Status,
    ) {
        vm.back()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        when (state.screen) {
            Screen.Status -> StatusScreen(vm, state)
            Screen.Install -> InstallScreen(vm, state)
            Screen.Default -> DefaultScreen(vm, state)
        }

        state.toast?.let { message ->
            Toast(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
            )
        }

        state.dialog?.let { dialog ->
            DialogHost(vm, dialog)
        }
    }
}

@Composable
private fun Toast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xEE263238), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = message, color = Color.White, fontSize = BodySize)
    }
}
