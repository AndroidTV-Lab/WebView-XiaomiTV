package com.tvlaunchers.webviewinstaller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import com.tvlaunchers.webviewinstaller.ui.InstallerApp
import com.tvlaunchers.webviewinstaller.ui.InstallerColorScheme

class MainActivity : ComponentActivity() {
    private var installerViewModel: InstallerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
        }

        setContent {
            MaterialTheme(colorScheme = InstallerColorScheme) {
                val vm: InstallerViewModel = viewModel()
                installerViewModel = vm
                InstallerApp(vm = vm, onExit = { finish() })
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            installerViewModel?.onStoragePermissionChanged()
        }
    }

    companion object {
        private const val REQ_STORAGE = 1001
    }
}
