package io.embrace.android.embracesdk.fakes

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowActivityManager

internal data class TestAeiData(
    val reason: Int,
    val status: Int,
    val description: String,
    val trace: String? = null,
    val timestamp: Long = 15000000000L,
    val pid: Int = 6952,
    val pss: Long = 1509123409L,
    val rss: Long = 1123409L,
    val importance: Int = 100,
    val processStateSummary: String? = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d_2f003c4d5e6f7a8b9c0d1e2f3a4b5c6d",
    val sessionIdError: String? = "",
    val traceStatus: String? = null,
) {

    fun toAeiObject(): ApplicationExitInfo = ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
        .setTimestamp(timestamp)
        .setPid(pid)
        .setProcessStateSummary(processStateSummary?.toByteArray())
        .setImportance(importance)
        .setPss(pss)
        .setRss(rss)
        .setReason(reason)
        .setStatus(status)
        .setDescription(description)
        .setTraceInputStream(trace?.byteInputStream())
        .build()
}

internal fun setupFakeAeiData(data: List<ApplicationExitInfo>) {
    val ctx = ApplicationProvider.getApplicationContext<Application>()
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val shadowActivityManager: ShadowActivityManager = Shadows.shadowOf(am)
    data.forEach(shadowActivityManager::addApplicationExitInfo)
}
