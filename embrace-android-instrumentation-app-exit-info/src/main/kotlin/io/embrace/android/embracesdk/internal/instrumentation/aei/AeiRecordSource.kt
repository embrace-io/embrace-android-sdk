package io.embrace.android.embracesdk.internal.instrumentation.aei

/**
 * Returns the most recent process exit records, newest first.
 */
internal fun interface AeiRecordSource {
    fun getHistoricalProcessExitReasons(maxNum: Int): List<AeiRecord>
}
