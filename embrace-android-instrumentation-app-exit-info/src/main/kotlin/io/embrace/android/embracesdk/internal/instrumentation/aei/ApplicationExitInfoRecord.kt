package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import java.io.InputStream

@RequiresApi(VERSION_CODES.R)
internal class ApplicationExitInfoRecord(private val info: ApplicationExitInfo) : AeiRecord {
    override val timestamp: Long get() = info.timestamp
    override val pid: Int get() = info.pid
    override val processStateSummary: ByteArray? get() = info.processStateSummary
    override val importance: Int get() = info.importance
    override val pss: Long get() = info.pss
    override val rss: Long get() = info.rss
    override val reason: Int get() = info.reason
    override val status: Int get() = info.status
    override val description: String? get() = info.description
    override val traceInputStream: InputStream? get() = info.traceInputStream
}
