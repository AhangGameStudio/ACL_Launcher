package com.acl.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.acl.launcher.core.Settings
import com.acl.launcher.ui.MainScreen
import com.acl.launcher.ui.SplashScreen
import com.acl.launcher.ui.theme.AclTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = Settings.get(this)
        // 整合包产出目录：应用私有目录下的 ACL/Packs
        val packsRoot = File(filesDir, "ACL/Packs").apply { mkdirs() }

        setContent {
            AclTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    MainScreen(settings, packsRoot)
                }
            }
        }
    }
}
