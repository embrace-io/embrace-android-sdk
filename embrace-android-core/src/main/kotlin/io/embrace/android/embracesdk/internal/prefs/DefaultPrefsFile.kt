package io.embrace.android.embracesdk.internal.prefs

import android.content.Context
import java.io.File

/**
 * The file behind the app's default `SharedPreferences`, which holds the SDK's legacy key-value store.
 */
@Suppress("EmbraceContextDirAccess")
fun defaultPrefsFile(context: Context): File =
    File(File(context.applicationInfo.dataDir, "shared_prefs"), "${context.packageName}_preferences.xml")
