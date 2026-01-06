package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.AeiLog
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

@RequiresApi(VERSION_CODES.R)
internal class AeiDataSourceImpl(
    args: InstrumentationArgs,
    private val backgroundWorker: BackgroundWorker,
    private val activityManager: ActivityManager,
    private val store: KeyValueStore,
    private val ordinalStore: OrdinalStore,
    private val versionChecker: VersionChecker = BuildVersionChecker,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { SDK_AEI_SEND_LIMIT },
    instrumentationName = "aei_data_source"
) {

    private companion object {
        private const val SDK_AEI_SEND_LIMIT = 64
        private const val AEI_HASH_CODES = "io.embrace.aeiHashCode"
    }

    override fun onDataCaptureEnabled() {
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
        val deliveredIds = store.deliveredAeiIds

        // update persisted state on what records were sent
        val unsentRecords = records.filter { !deliveredIds.contains(it.getAeiId()) }
        val sentRecords = records.minus(unsentRecords.toSet())
        store.deliveredAeiIds = sentRecords.map { it.getAeiId() }.toSet()

        unsentRecords.forEach {
            val obj = it.constructAeiObject(versionChecker, configService.appExitInfoBehavior.getTraceMaxLimit())
            val crashNumber = obj?.getOrdinal { ordinalStore.incrementAndGet(Ordinal.CRASH) }
            val aeiNumber = obj?.getOrdinal { ordinalStore.incrementAndGet(Ordinal.AEI_CRASH) }
            if (obj == null) {
                return@forEach
            }
            captureTelemetry {
                val capture = configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled() || !obj.hasNativeTombstone()
                if (capture) {
                    val schemaType = AeiLog(
                        sessionId = obj.sessionId,
                        sessionIdError = obj.sessionIdError,
                        importance = obj.importance,
                        pss = obj.pss,
                        reason = obj.reason,
                        rss = obj.rss,
                        status = obj.status,
                        timestamp = obj.timestamp,
                        description = obj.description,
                        traceStatus = obj.traceStatus,
                        crashNumber = crashNumber,
                        aeiNumber = aeiNumber
                    )
                    addLog(schemaType, LogSeverity.INFO, obj.trace ?: "")
                }

                // always count AEI as delivered once we process it & submit to the OTel logging system, or discard
                store.deliveredAeiIds = store.deliveredAeiIds.plus(obj.getAeiId())
            }
        }
    }

    private fun AppExitInfoData.hasNativeTombstone(): Boolean {
        return trace != null && reason == ApplicationExitInfo.REASON_CRASH_NATIVE
    }

    private inline fun AppExitInfoData.getOrdinal(provider: () -> Int) = when (hasNativeTombstone()) {
        true -> provider()
        else -> null
    }

    private var KeyValueStore.deliveredAeiIds: Set<String>
        get() = store.getStringSet(AEI_HASH_CODES) ?: emptySet()
        set(value) = store.edit { putStringSet(AEI_HASH_CODES, value) }

    private fun ApplicationExitInfo.getAeiId(): String = "${timestamp}_$pid"
    private fun AppExitInfoData.getAeiId(): String = "${timestamp}_$pid"
}
