package com.openlistmobile.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOWNLOAD_DIR_URI = "download_dir_uri"
    }

    var downloadDirUri: Uri?
        get() = prefs.getString(KEY_DOWNLOAD_DIR_URI, null)?.let { Uri.parse(it) }
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_DIR_URI, value?.toString()).apply()
        }
}
