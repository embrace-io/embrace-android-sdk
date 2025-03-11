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
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppExitInfoData
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

@RequiresApi(VERSION_CODES.R)
internal class AeiDataSourceImpl(
    private val backgroundWorker: BackgroundWorker,
    private val configService: ConfigService,
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
        private const val SDK_AEI_SEND_LIMIT = 64
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
        val maxNum = configService.appExitInfoBehavior.appExitInfoMaxNum()
        val records = activityManager.getHistoricalProcessExitReasons(null, 0, maxNum).take(SDK_AEI_SEND_LIMIT)
        val deliveredIds = preferencesService.deliveredAeiIds

        // update persisted state on what records were sent
        val unsentRecords = records.filter { !deliveredIds.contains(it.getAeiId()) }
        val sentRecords = records.minus(unsentRecords.toSet())
        preferencesService.deliveredAeiIds = sentRecords.map { it.getAeiId() }.toSet()

        unsentRecords.forEach {
            val obj = it.constructAeiObject(versionChecker, configService.appExitInfoBehavior.getTraceMaxLimit())
            val crashNumber = obj?.getOrdinal(preferencesService::incrementAndGetCrashNumber)
            val aeiNumber = obj?.getOrdinal(preferencesService::incrementAndGetAeiCrashNumber)
            if (obj == null) {
                return@forEach
            }
            captureData(
                inputValidation = NoInputValidation,
                captureAction = {
                    val capture = configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled() || !obj.hasNativeTombstone()
                    if (capture) {
                        val schemaType = AeiLog(obj, crashNumber, aeiNumber)
                        addLog(schemaType, INFO.toOtelSeverity(), obj.trace ?: "")
                    }

                    // always count AEI as delivered once we process it & submit to the OTel logging system, or discard
                    preferencesService.deliveredAeiIds = preferencesService.deliveredAeiIds.plus(obj.getAeiId())
                }
            )
        }
    }

    private fun AppExitInfoData.hasNativeTombstone(): Boolean {
        return trace != null && reason == ApplicationExitInfo.REASON_CRASH_NATIVE
    }

    private inline fun AppExitInfoData.getOrdinal(provider: () -> Int) = when (hasNativeTombstone()) {
        true -> provider()
        else -> null
    }

    private fun ApplicationExitInfo.getAeiId(): String = "${timestamp}_$pid"
    private fun AppExitInfoData.getAeiId(): String = "${timestamp}_$pid"
}
