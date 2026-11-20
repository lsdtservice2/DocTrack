package com.logisparktech.doctrack

import androidx.compose.ui.window.ComposeUIViewController
import com.logisparktech.doctrack.app.App
import com.logisparktech.doctrack.di.initKoin
import com.logisparktech.doctrack.platform.IosNotificationDelegate
import platform.UIKit.UIViewController

/**
 * Call from the Swift App initializer, before the UI is created, so notification taps that
 * launch the app are delivered to the delegate. Safe to call more than once.
 */
fun initDocTrack() {
    initKoin()
    IosNotificationDelegate.install()
}

fun MainViewController(): UIViewController {
    initDocTrack()
    return ComposeUIViewController { App() }
}
