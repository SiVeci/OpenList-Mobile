package com.openlistmobile.app.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor() {
    var currentProfileId: Long = -1L
    var currentToken: String? = null
    var currentServerUrl: String? = null
}
