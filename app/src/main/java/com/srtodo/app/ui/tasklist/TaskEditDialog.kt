package com.srtodo.app.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.srtodo.app.R
import com.srtodo.app.util.DueFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class PickerStep { None, Date, Time }

/**
 * タスクの追加・編集ダイアログ。
 * 期限は「日付を選ぶ → 時刻を選ぶ」の順で設定する。[onDelete] が null なら削除ボタンを出さない（新規追加時）。
 */
@Composable
fun TaskEditDialog(
    key: Long,
    initialTitle: String,
    initialDueAt: Long?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, dueAt: Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by rememberSaveable(key) { mutableStateOf(initialTitle) }
    var dueAt by rememberSaveable(key) { mutableStateOf(initialDueAt) }
    var step by rememberSaveable(key) { mutableStateOf(PickerStep.None) }
    // 日付を選んでから時刻を選ぶまでの一時的な値
    var pickedDate by rememberSaveable(key) { mutableStateOf<LocalDate?>(null) }

    val focusRequester = remember { FocusRequester() }
    if (isNew) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNew) R.string.dialog_title_add else R.string.dialog_title_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                Text(
                    text = stringResource(R.string.field_due),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = PickerStep.Date }) {
                        Text(dueAt?.let(DueFormatter::formatDateTime) ?: stringResource(R.string.due_set))
                    }
                    if (dueAt != null) {
                        TextButton(onClick = { dueAt = null }) {
                            Text(stringResource(R.string.due_clear))
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, dueAt) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    // 期限が未設定なら、次の正時（例: 14:20 → 15:00）を初期値にする
    val base = remember(dueAt) {
        dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            ?: LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)
    }
    when (step) {
        PickerStep.None -> Unit
        PickerStep.Date -> DueDatePickerDialog(
            initialDate = base.toLocalDate(),
            onDismiss = { step = PickerStep.None },
            onConfirm = { date ->
                pickedDate = date
                step = PickerStep.Time
            },
        )
        PickerStep.Time -> DueTimePickerDialog(
            initialTime = base.toLocalTime(),
            onDismiss = { step = PickerStep.None },
            onConfirm = { time ->
                val date = pickedDate ?: base.toLocalDate()
                dueAt = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                step = PickerStep.None
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // DatePicker は UTC の 0 時を基準にした epoch millis で日付をやり取りする
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
