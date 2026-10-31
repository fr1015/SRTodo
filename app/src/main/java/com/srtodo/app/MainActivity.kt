package com.srtodo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.srtodo.app.ui.theme.SRTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { appContainer }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshNotification()
            } else {
                Toast.makeText(this, R.string.debug_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshNotificationOrRequestPermission()

        setContent {
            SRTodoTheme {
                val pending by container.taskRepository.observePending()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val completed by container.taskRepository.observeCompleted()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 動作確認用の仮画面。Phase 4 でタスク一覧に置き換える
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("SRTodo", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.debug_counts, pending.size, completed.size))
                        Button(onClick = { refreshNotificationOrRequestPermission() }) {
                            Text(stringResource(R.string.debug_show_notification))
                        }
                        OutlinedButton(onClick = {
                            lifecycleScope.launch { container.taskRepository.replaceWithSamples() }
                        }) {
                            Text(stringResource(R.string.debug_reset_samples))
                        }
                    }
                }
            }
        }
    }

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
        lifecycleScope.launch { container.notificationUpdater.update() }
    }
}
