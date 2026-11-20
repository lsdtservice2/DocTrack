package com.logisparktech.doctrack.di

import com.logisparktech.doctrack.platform.androidPlatformModule
import org.koin.core.module.Module

actual val platformModule: Module = androidPlatformModule()
