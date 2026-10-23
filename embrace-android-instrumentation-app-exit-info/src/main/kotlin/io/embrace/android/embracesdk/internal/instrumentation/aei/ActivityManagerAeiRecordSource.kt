package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi

@RequiresApi(VERSION_CODES.R)
internal class ActivityManagerAeiRecordSource(
    private val activityManager: ActivityManager,
) : AeiRecordSource {
    override fun getHistoricalProcessExitReasons(maxNum: Int): List<AeiRecord> =
        activityManager.getHistoricalProcessExitReasons(null, 0, maxNum).map(::ApplicationExitInfoRecord)
}
