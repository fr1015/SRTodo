package com.srtodo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.srtodo.app.R

/**
 * Material3 の ColorScheme に無い、このアプリ独自の色。
 * 画面からは `SRTodoTheme.colors.overdue` のように参照する。
 */
@Immutable
data class SRTodoColors(
    /** 期限切れタスクの文字色。通知と同じ色リソース（values / values-night の task_overdue）を使う */
    val overdue: Color,
)

private val LocalSRTodoColors = staticCompositionLocalOf { SRTodoColors(overdue = Color.Red) }

// 仮テーマ（Material3 標準＋ダイナミックカラー）。デザイン確定後（Phase 5）に色・タイポグラフィを差し替える
@Composable
fun SRTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val colors = SRTodoColors(overdue = colorResource(R.color.task_overdue))
    CompositionLocalProvider(LocalSRTodoColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object SRTodoTheme {
    val colors: SRTodoColors
        @Composable get() = LocalSRTodoColors.current
}
