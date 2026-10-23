package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.os.Build
import android.os.Trace
import androidx.annotation.RequiresApi

internal object AndroidTraceApi : TraceApi {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun isEnabled(): Boolean = Trace.isEnabled()

    @SuppressLint("UnclosedTrace")
    override fun beginSection(sectionName: String) = Trace.beginSection(sectionName)

    override fun endSection() = Trace.endSection()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun setCounter(counterName: String, counterValue: Long) = Trace.setCounter(counterName, counterValue)
}
