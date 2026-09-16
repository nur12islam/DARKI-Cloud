package com.darki.cloud.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("darki_cloud_session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString(KEY_TOKEN, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    var userId: String?
        get() = preferences.getString(KEY_USER_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_USER_ID) else putString(KEY_USER_ID, value)
        }.apply()

    var rootFolderId: String?
        get() = preferences.getString(KEY_ROOT_FOLDER_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_ROOT_FOLDER_ID) else putString(KEY_ROOT_FOLDER_ID, value)
        }.apply()

    var deviceId: String?
        get() = preferences.getString(KEY_DEVICE_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, value)
        }.apply()

    var cursor: String
        get() = preferences.getString(KEY_CURSOR, "0") ?: "0"
        set(value) = preferences.edit().putString(KEY_CURSOR, value).apply()

    fun clear() = preferences.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_ROOT_FOLDER_ID = "root_folder_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_CURSOR = "sync_cursor"
    }
}
