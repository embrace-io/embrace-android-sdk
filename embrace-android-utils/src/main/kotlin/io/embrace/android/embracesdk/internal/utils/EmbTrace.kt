package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Trace

/**
 * Shim to add custom events to system traces for API 29+. Basic alternative to using androidx.tracing that is safe to interleave.
 */
object EmbTrace {

    const val MAX_KEY_LENGTH = 127

    /**
     * Create a trace section around the lambda passed in and return the result.
     * The name of the section will be [sectionName] prefixed by "emb-" and truncated to 127 characters.
     *
     * No section is started (and [code] is simply invoked) when running on a version earlier than
     * [VERSION_CODES.Q] or when no system trace is currently being captured. Whether a section was
     * opened is captured once up front, so the section stays balanced even if tracing is toggled
     * while [code] runs.
     */
    @SuppressLint("UnclosedTrace")
    inline fun <T> trace(sectionName: String, code: Provider<T>): T {
        val enabled = Build.VERSION.SDK_INT >= VERSION_CODES.Q && Trace.isEnabled()
        if (enabled) {
            // android.os.Trace rejects section names longer than 127 chars, so only pay for the
            // extra substring allocation when the name actually exceeds that limit.
            val name = "emb-$sectionName"
            Trace.beginSection(
                when {
                    name.length > MAX_KEY_LENGTH -> name.substring(0, MAX_KEY_LENGTH)
                    else -> name
                },
            )
        }
        try {
            return code()
        } finally {
            if (enabled) {
                Trace.endSection()
            }
        }
    }
}
