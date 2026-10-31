package com.sptodo.app

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sptodo.app.notification.PrototypeTasks
import com.sptodo.app.notification.TaskNotifier
import com.sptodo.app.ui.theme.SPToDoTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                TaskNotifier.show(this)
            } else {
                Toast.makeText(this, R.string.prototype_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TaskNotifier.createChannel(this)
        showNotificationOrRequestPermission()

        setContent {
            SPToDoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Phase 1 の試作用画面。Phase 4 でタスク一覧に置き換える
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("SPToDo")
                        Button(onClick = { showNotificationOrRequestPermission() }) {
                            Text(stringResource(R.string.prototype_show_notification))
                        }
                        OutlinedButton(onClick = {
                            PrototypeTasks.reset()
                            showNotificationOrRequestPermission()
                        }) {
                            Text(stringResource(R.string.prototype_reset))
                        }
                    }
                }
            }
        }
    }

    private fun showNotificationOrRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            TaskNotifier.show(this)
        } else {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
