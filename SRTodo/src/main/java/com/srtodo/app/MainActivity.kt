package com.srtodo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.srtodo.app.ui.settings.SettingsScreen
import com.srtodo.app.ui.tasklist.TaskListScreen
import com.srtodo.app.ui.theme.SRTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshNotification()
            } else {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            refreshNotificationOrRequestPermission()
        }
        setContent {
            SRTodoTheme {
                // 画面は2つだけなので、ナビゲーションのライブラリは使わず状態で切り替える
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    TaskListScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    /**
     * 起動時に通知を作り直す（固定表示ならサービスを開始する）。強制停止のあとなど、通知が消えている場合に戻すため。
     * 通知の権限がなければリクエストする。
     */
    private fun refreshNotificationOrRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            refreshNotification()
        } else {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshNotification() {
        lifecycleScope.launch { appContainer.notificationController.refresh() }
    }
}
