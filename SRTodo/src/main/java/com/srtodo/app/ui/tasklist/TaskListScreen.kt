package com.srtodo.app.ui.tasklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.srtodo.app.R
import com.srtodo.app.data.Task
import com.srtodo.app.ui.theme.SRTodoTheme
import com.srtodo.app.util.DueFormatter
import kotlinx.coroutines.delay

/** 編集ダイアログで「新規追加」を表す ID（Room の自動採番は 1 から始まるので衝突しない） */
private const val NEW_TASK_ID = 0L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(viewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory)) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val now by rememberCurrentTimeMinutely()

    // 編集中のタスクID（null はダイアログを閉じている）。画面回転でも保てるよう ID で持つ
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingId = NEW_TASK_ID }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_add),
                )
            }
        },
    ) { innerPadding ->
        if (pending.isEmpty() && completed.isEmpty()) {
            EmptyState(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                // FAB に最後の行が隠れないよう下に余白を取る
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (pending.isEmpty()) {
                    item(key = "pending-empty") {
                        Text(
                            text = stringResource(R.string.empty_pending),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 完了・未完了は別々の Flow から来るため、切り替わる瞬間にキーが重ならないよう接頭辞を付ける
                items(pending, key = { "p-${it.id}" }) { task ->
                    TaskRow(
                        task = task,
                        now = now,
                        onCheckedChange = { viewModel.setDone(task.id, it) },
                        onClick = { editingId = task.id },
                    )
                }
                if (completed.isNotEmpty()) {
                    item(key = "completed-header") {
                        CompletedHeader(
                            count = completed.size,
                            expanded = showCompleted,
                            onToggle = { showCompleted = !showCompleted },
                        )
                    }
                    if (showCompleted) {
                        items(completed, key = { "c-${it.id}" }) { task ->
                            TaskRow(
                                task = task,
                                now = now,
                                onCheckedChange = { viewModel.setDone(task.id, it) },
                                onClick = { editingId = task.id },
                                onDelete = { viewModel.delete(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val task = if (id == NEW_TASK_ID) null else (pending + completed).find { it.id == id }
        if (id != NEW_TASK_ID && task == null) {
            // 編集中に削除・Flow の更新待ちなどで見つからないときは閉じる
            return@let
        }
        TaskEditDialog(
            key = id,
            initialTitle = task?.title.orEmpty(),
            initialDueAt = task?.dueAt,
            isNew = task == null,
            onDismiss = { editingId = null },
            onSave = { title, dueAt ->
                if (task == null) viewModel.add(title, dueAt) else viewModel.edit(task.id, title, dueAt)
                editingId = null
            },
            onDelete = task?.let {
                {
                    viewModel.delete(it.id)
                    editingId = null
                }
            },
        )
    }
}

@Composable
private fun TaskRow(
    task: Task,
    now: Long,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val overdue = task.isOverdue(now)
    val titleColor = when {
        overdue -> SRTodoTheme.colors.overdue
        task.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subText = if (task.isDone && task.completedAt != null) {
        stringResource(R.string.completed_at, DueFormatter.formatDateTime(task.completedAt))
    } else {
        DueFormatter.format(context, task, now)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isDone, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) SRTodoTheme.colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.completed_header, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand),
                contentDescription = stringResource(
                    if (expanded) R.string.action_collapse else R.string.action_expand
                ),
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.empty_all),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 現在時刻を毎分0秒に更新する。期限切れの赤字表示を時間の経過に合わせて切り替えるため */
@Composable
private fun rememberCurrentTimeMinutely(): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            val current = System.currentTimeMillis()
            value = current
            delay(60_000 - current % 60_000)
        }
    }
