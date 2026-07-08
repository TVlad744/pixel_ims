package com.takaisaisei.pixelims

import android.os.Build

/**
 * - **Attached** (Android 14+): `am instrument --no-restart` keeps the UI process alive; the run is
 *   synchronous and the result is shown live in the UI.
 * - **Detached** (Android 12–13): `am instrument` force-stops the app, so the run is fired
 *   detached and the restarted broker reports the result via notifications.
 */
object ApplyMode {

    val detached: Boolean
        get() = BuildConfig.FORCE_DETACHED ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
