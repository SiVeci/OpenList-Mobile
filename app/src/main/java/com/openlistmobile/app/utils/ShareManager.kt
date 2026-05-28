package com.openlistmobile.app.utils

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareManager @Inject constructor() {
    private val _sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val sharedUris: StateFlow<List<Uri>> = _sharedUris.asStateFlow()

    fun setSharedUris(uris: List<Uri>) {
        _sharedUris.value = uris
    }

    fun clearSharedUris() {
        _sharedUris.value = emptyList()
    }
}
