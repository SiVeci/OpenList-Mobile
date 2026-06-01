package com.openlistmobile.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            focusManager.clearFocus(force = true)
        }
    }
}
