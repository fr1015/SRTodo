package com.srtodo.app.settings

import android.content.Context
import androidx.core.content.edit

/** アプリの設定。項目が少ないので SharedPreferences に保存する（ライブラリは増やさない） */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 「通知に固定表示する」。オンならフォアグラウンドサービスで通知欄の一番上に固定する */
    var pinNotification: Boolean
        get() = prefs.getBoolean(KEY_PIN_NOTIFICATION, true)
        set(value) = prefs.edit { putBoolean(KEY_PIN_NOTIFICATION, value) }

    private companion object {
        const val FILE_NAME = "settings"
        const val KEY_PIN_NOTIFICATION = "pin_notification"
    }
}
