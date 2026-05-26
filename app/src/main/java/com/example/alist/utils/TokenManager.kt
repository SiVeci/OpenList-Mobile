package com.example.alist.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor() {
    var currentToken: String? = null
    var currentServerUrl: String? = null
}
