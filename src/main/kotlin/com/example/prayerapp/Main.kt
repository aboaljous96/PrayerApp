package com.example.prayerapp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// التأكد من أن الـ imports صحيحة
import com.example.prayerapp.PrayerTheme
import com.example.prayerapp.PrayerTimesApp
import kotlinx.coroutines.delay

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Fullscreen)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "مواقيت الصلاة",
        alwaysOnTop = true,
        undecorated = true
    ) {
        val composeWindow = window
        LaunchedEffect(Unit) {
            while (true) {
                delay(5 * 60 * 1000L)
                if (windowState.isMinimized) {
                    windowState.isMinimized = false
                    windowState.placement = WindowPlacement.Fullscreen
                    composeWindow.toFront()
                    composeWindow.requestFocus()
                }
            }
        }
        PrayerTheme { PrayerTimesApp() }
    }
}
