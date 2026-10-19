package com.tvlaunchers.webviewinstaller

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import com.tvlaunchers.webviewinstaller.ui.InstallerApp
import com.tvlaunchers.webviewinstaller.ui.InstallerColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(colorScheme = InstallerColorScheme) {
                val vm: InstallerViewModel = viewModel()
                InstallerApp(vm = vm, onExit = { finish() })
            }
        }
    }
}
