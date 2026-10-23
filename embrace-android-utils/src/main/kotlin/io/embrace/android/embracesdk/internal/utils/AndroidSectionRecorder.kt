package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.os.Build.VERSION_CODES

/**
 * Records sections into system traces via `android.os.Trace`, for the JVM-only modules
 * that reach tracing through [SystemTrace] because they cannot reference that class at compile time.
 */
object AndroidSectionRecorder : SectionRecorder {

    internal var versionChecker: VersionChecker = BuildVersionChecker

    internal var traceApi: TraceApi = AndroidTraceApi

    @SuppressLint("UnclosedTrace")
    override fun beginSection(sectionName: String): Boolean {
        if (!versionChecker.isAtLeast(VERSION_CODES.Q) || !traceApi.isEnabled()) {
            return false
        }
        traceApi.beginSection(prefixedTraceName(sectionName))
        return true
    }

    override fun endSection() {
        if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
            traceApi.endSection()
        }
    }

    override fun setCounter(name: String, value: Long) {
        if (!versionChecker.isAtLeast(VERSION_CODES.Q) || !traceApi.isEnabled()) {
            return
        }
        traceApi.setCounter(prefixedTraceName(name), value)
    }
}
