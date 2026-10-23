package io.embrace.android.embracesdk.internal.utils

import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi

/**
 * The `android.os.Trace` calls made by [EmbTrace] and [AndroidSectionRecorder], swappable so tests run on the JVM.
 */
interface TraceApi {
    @RequiresApi(VERSION_CODES.Q)
    fun isEnabled(): Boolean

    fun beginSection(sectionName: String)

    fun endSection()

    @RequiresApi(VERSION_CODES.Q)
    fun setCounter(counterName: String, counterValue: Long)
}
