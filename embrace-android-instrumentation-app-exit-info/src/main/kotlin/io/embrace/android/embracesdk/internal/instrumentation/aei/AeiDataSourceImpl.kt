package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.AeiLog
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

@RequiresApi(VERSION_CODES.R)
internal class AeiDataSourceImpl(
    args: InstrumentationArgs,
    private val backgroundWorker: BackgroundWorker,
    private val recordSourceProvider: Provider<AeiRecordSource?>,
    private val store: KeyValueStore,
    private val ordinalStore: OrdinalStore,
    private val versionChecker: VersionChecker = BuildVersionChecker,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { SDK_AEI_SEND_LIMIT },
    instrumentationName = "aei_data_source",
) {

    private companion object {
        // matches the default max records AOSP retains per package
        // (config_app_exit_info_history_list_size)
        private const val SDK_AEI_SEND_LIMIT = 16
        private const val AEI_HASH_CODES = "io.embrace.aeiHashCode"
    }

    private val lock = Any()
    private var closed = false

    override fun onDataCaptureEnabled() {
        backgroundWorker.submit {
            errorHandler.guardSdkCode(SdkErrorType.ProcessAeiRecords, ::processAeiRecords)
        }
    }

    override fun onDataCaptureDisabled() {
        synchronized(lock) {
            closed = true
        }
    }

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    private fun processAeiRecords() {
        if (isClosed()) {
            return
        }
        val recordSource = recordSourceProvider() ?: return
        val maxNum = configService.config.aei.maxNum
        val records = recordSource.getHistoricalProcessExitReasons(maxNum).take(SDK_AEI_SEND_LIMIT)
        val deliveredIds = store.deliveredAeiIds

        // update persisted state on what records were sent
        val unsentRecords = records.filter { !deliveredIds.contains(it.getAeiId()) }
        val sentRecords = records.minus(unsentRecords.toSet())
        synchronized(lock) {
            if (closed) {
                return
            }
            store.deliveredAeiIds = sentRecords.map { it.getAeiId() }.toSet()
        }

        unsentRecords.forEach {
            val obj = it.constructAeiObject(versionChecker, configService.config.aei.traceMaxLimit) ?: return@forEach
            // hold the lock so nothing is persisted after the SDK has been disabled
            synchronized(lock) {
                if (closed) {
                    return
                }
                val crashNumber = obj.getOrdinal { ordinalStore.incrementAndGet(Ordinal.CRASH) }
                val aeiNumber = obj.getOrdinal { ordinalStore.incrementAndGet(Ordinal.AEI_CRASH) }
                captureRecord(obj, crashNumber, aeiNumber)
            }
        }
    }

    private fun captureRecord(obj: AppExitInfoData, crashNumber: Int?, aeiNumber: Int?) {
        captureTelemetry {
            val capture = configService.config.autoDataCapture.nativeCrashCaptureEnabled || !obj.hasNativeTombstone()
            if (capture) {
                val schemaType = AeiLog(
                    sessionPartId = obj.sessionPartId,
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
                    aeiNumber = aeiNumber,
                    userSessionId = obj.userSessionId,
                )
                addLog(schemaType, LogSeverity.INFO, obj.trace ?: "")
            }

            // always count AEI as delivered once we process it & submit to the OTel logging system, or discard
            store.deliveredAeiIds = store.deliveredAeiIds.plus(obj.getAeiId())
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
        set(value) = store.editAndCommit { putStringSet(AEI_HASH_CODES, value) }

    private fun AeiRecord.getAeiId(): String = "${timestamp}_$pid"
    private fun AppExitInfoData.getAeiId(): String = "${timestamp}_$pid"
}
