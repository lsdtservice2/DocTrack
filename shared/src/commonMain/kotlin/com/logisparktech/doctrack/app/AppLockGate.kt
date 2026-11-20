package com.logisparktech.doctrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.platform.BiometricAuthenticator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Re-lock only after the app has been in the background this long (so file pickers don't re-lock). */
private const val RELOCK_AFTER_MILLIS = 60_000L

@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val authenticator = koinInject<BiometricAuthenticator>()
    val dateProvider = koinInject<DateProvider>()
    var locked by rememberSaveable { mutableStateOf(enabled) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { backgroundedAt = dateProvider.nowMillis() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (enabled && backgroundedAt > 0 && dateProvider.nowMillis() - backgroundedAt > RELOCK_AFTER_MILLIS) {
            locked = true
        }
    }
    // Disabling the lock in settings unlocks immediately.
    LaunchedEffect(enabled) { if (!enabled) locked = false }

    if (enabled && locked && authenticator.isAvailable()) {
        LockScreen(authenticator, onUnlocked = { locked = false })
    } else {
        content()
    }
}

@Composable
private fun LockScreen(authenticator: BiometricAuthenticator, onUnlocked: () -> Unit) {
    val s = strings
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val unlock: () -> Unit = {
        scope.launch {
            if (authenticator.authenticate(s.unlock, s.unlockReason, s.cancel)) onUnlocked() else failed = true
        }
    }
    LaunchedEffect(Unit) { unlock() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(20.dp))
            Text(s.appLocked, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (failed) s.authenticationFailed else s.unlockReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = unlock) { Text(s.unlock) }
        }
    }
}
