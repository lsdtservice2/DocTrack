package com.logisparktech.doctrack

import android.app.Application
import com.logisparktech.doctrack.platform.startDocTrack

class DocTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startDocTrack(this)
    }
}
