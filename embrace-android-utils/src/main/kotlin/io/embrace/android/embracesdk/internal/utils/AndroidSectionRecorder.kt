package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Trace

/**
 * Records sections into system traces via `android.os.Trace`, for the JVM-only modules
 * that reach tracing through [SystemTrace] because they cannot reference that class at compile time.
 */
object AndroidSectionRecorder : SectionRecorder {

    @SuppressLint("UnclosedTrace")
    override fun beginSection(sectionName: String): Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.Q || !Trace.isEnabled()) {
            return false
        }
        Trace.beginSection(prefixedTraceName(sectionName))
        return true
    }

    override fun endSection() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            Trace.endSection()
        }
    }
}
