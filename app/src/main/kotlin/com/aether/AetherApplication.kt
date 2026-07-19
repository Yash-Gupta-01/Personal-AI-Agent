package com.aether

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt's dependency graph root.
 *
 * Deliberately empty: no eager initialisation happens here. Work that needs to
 * run at startup should be an injected component with its own lifecycle, not a
 * line in this class — that keeps startup cost visible and testable.
 */
@HiltAndroidApp
class AetherApplication : Application()
