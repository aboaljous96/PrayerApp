package com.example.prayerapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// التأكد من أن الـ imports صحيحة
import com.example.prayerapp.PrayerTheme
import com.example.prayerapp.PrayerTimesApp

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Fullscreen)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "مواقيت الصلاة",
        alwaysOnTop = true,
        undecorated = true
    ) {
        PrayerTheme { PrayerTimesApp() }
    }
}
