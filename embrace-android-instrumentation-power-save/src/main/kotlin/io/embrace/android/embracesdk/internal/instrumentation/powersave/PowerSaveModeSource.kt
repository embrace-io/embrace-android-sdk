package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs

/**
 * Reads whether power save mode is on. [PowerManager] is final, so this lets tests fake it.
 */
internal fun interface PowerSaveModeSource {
    fun isPowerSaveMode(): Boolean
}

internal fun InstrumentationArgs.powerSaveModeSource(): PowerSaveModeSource? =
    systemService<PowerManager>(Context.POWER_SERVICE)?.let { PowerSaveModeSource(it::isPowerSaveMode) }
