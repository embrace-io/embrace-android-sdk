package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi

/**
 * Queries by package name rather than null. On API 30 a null package makes system_server merge and sort the records
 * of every package under the app's uid with an int-overflowing comparator, which can throw
 * "Comparison method violates its general contract!".
 */
@RequiresApi(VERSION_CODES.R)
internal class ActivityManagerAeiRecordSource(
    private val activityManager: ActivityManager,
    private val packageName: String,
) : AeiRecordSource {
    override fun getHistoricalProcessExitReasons(maxNum: Int): List<AeiRecord> =
        activityManager.getHistoricalProcessExitReasons(packageName, 0, maxNum).map(::ApplicationExitInfoRecord)
}
