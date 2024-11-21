package io.embrace.android.embracesdk.internal.capture.aei

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.Severity.INFO
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.AeiLog
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

@RequiresApi(VERSION_CODES.R)
internal class AeiDataSourceImpl(
    private val backgroundWorker: BackgroundWorker,
    private val appExitInfoBehavior: AppExitInfoBehavior,
    private val activityManager: ActivityManager,
    private val preferencesService: PreferencesService,
    logWriter: LogWriter,
    private val logger: EmbLogger,
    private val versionChecker: VersionChecker = BuildVersionChecker,
) : AeiDataSource, LogDataSourceImpl(
    logWriter,
    logger,
    limitStrategy = UpToLimitStrategy { SDK_AEI_SEND_LIMIT }
) {

    private companion object {
        private const val SDK_AEI_SEND_LIMIT = 32
    }

    override fun enableDataCapture() {
        backgroundWorker.submit {
            try {
                processAeiRecords()
            } catch (exc: Throwable) {
                logger.trackInternalError(InternalErrorType.ENABLE_DATA_CAPTURE, exc)
            }
        }
    }

    private fun processAeiRecords() {
        val maxNum = appExitInfoBehavior.appExitInfoMaxNum()
        val records = activityManager.getHistoricalProcessExitReasons(null, 0, maxNum).take(SDK_AEI_SEND_LIMIT)
        val unsentRecords = getUnsentRecords(records)

        unsentRecords.forEach {
            val obj = it.constructAeiObject(versionChecker, appExitInfoBehavior.getTraceMaxLimit()) ?: return@forEach
            captureData(
                inputValidation = NoInputValidation,
                captureAction = {
                    val schemaType = AeiLog(obj)
                    addLog(schemaType, INFO.toOtelSeverity(), obj.trace ?: "")
                }
            )
        }
    }

    /**
     * Calculates what AEI records have been sent by subtracting a collection of IDs that have been previously
     * sent from the return value of getHistoricalProcessExitReasons.
     */
    private fun getUnsentRecords(records: List<ApplicationExitInfo>): List<ApplicationExitInfo> {
        val deliveredIds = preferencesService.deliveredAeiIds
        preferencesService.deliveredAeiIds = records.map { it.getAeiId() }.toSet()
        return records.filter { !deliveredIds.contains(it.getAeiId()) }
    }

    private fun ApplicationExitInfo.getAeiId(): String = "${timestamp}_$pid"
}
