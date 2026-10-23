package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ApplicationExitInfo
import java.io.InputStream

/**
 * The fields the SDK reads from an [ApplicationExitInfo]. The framework class is final, so this lets tests fake it.
 */
internal interface AeiRecord {
    val timestamp: Long
    val pid: Int
    val processStateSummary: ByteArray?
    val importance: Int
    val pss: Long
    val rss: Long
    val reason: Int
    val status: Int
    val description: String?
    val traceInputStream: InputStream?
}
