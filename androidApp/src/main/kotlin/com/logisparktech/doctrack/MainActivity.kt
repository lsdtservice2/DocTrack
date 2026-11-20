package com.logisparktech.doctrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.logisparktech.doctrack.app.App
import com.logisparktech.doctrack.platform.ActivityHolder
import com.logisparktech.doctrack.platform.handleNotificationIntent

/** FragmentActivity is required by the AndroidX BiometricPrompt used for the optional app lock. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ActivityHolder.current = this
        handleNotificationIntent(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.current = this
    }

    override fun onDestroy() {
        if (ActivityHolder.current === this) ActivityHolder.current = null
        super.onDestroy()
    }
}
