package io.embrace.android.embracesdk.capture.aei

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logInfoWithException
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logWarningWithException
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.BlobSession
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.io.IOException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(VERSION_CODES.R)
internal class EmbraceApplicationExitInfoService(
    private val backgroundWorker: BackgroundWorker,
    private val configService: ConfigService,
    private val activityManager: ActivityManager?,
    private val preferencesService: PreferencesService,
    private val deliveryService: DeliveryService,
    private val metadataService: MetadataService,
    private val userService: UserService,
    private val buildVersionChecker: VersionChecker = BuildVersionChecker
) : ApplicationExitInfoService, ConfigListener {

    companion object {
        private const val SDK_AEI_SEND_LIMIT = 32
    }

    @Volatile
    var backgroundExecution: Future<*>? = null

    private val sessionApplicationExitInfoData: MutableList<AppExitInfoData> = mutableListOf()
    private var isSessionApplicationExitInfoDataReady = AtomicBoolean(false)

    init {
        configService.addListener(this)
        if (configService.isAppExitInfoCaptureEnabled()) {
            startService()
        }
    }

    private fun startService() {
        backgroundExecution = backgroundWorker.submit {
            try {
                processApplicationExitInfo()
            } catch (exc: Throwable) {
                logWarningWithException(
                    "AEI - Failed to process AEIs due to unexpected error",
                    exc,
                    true
                )
            }
        }
    }

    private fun processApplicationExitInfo() {
        val historicalProcessExitReasons = getHistoricalProcessExitReasons()

        val unsentExitReasons = getUnsentExitReasons(historicalProcessExitReasons)

        unsentExitReasons.forEach {
            sessionApplicationExitInfoData.add(buildSessionAppExitInfoData(it, null, null))
        }

        isSessionApplicationExitInfoDataReady.set(true)

        // now send AEIs with blobs.
        processApplicationExitInfoBlobs(unsentExitReasons)
    }

    private fun processApplicationExitInfoBlobs(unsentExitReasons: List<ApplicationExitInfo>) {
        unsentExitReasons.forEach { aei: ApplicationExitInfo ->
            val traceResult = collectExitInfoTrace(aei)
            if (traceResult != null) {
                val payload = buildSessionAppExitInfoData(
                    aei,
                    getTrace(traceResult),
                    getTraceStatus(traceResult)
                )
                sendApplicationExitInfoWithTraces(listOf(payload))
            }
        }
    }

    private fun getHistoricalProcessExitReasons(): List<ApplicationExitInfo> {
        // A process ID that used to belong to this package but died later;
        // a value of 0 means to ignore this parameter and return all matching records.
        val pid = 0

        // number of results to be returned; a value of 0 means to ignore this parameter and return
        // all matching records with a maximum of 16 entries
        val maxNum = configService.appExitInfoBehavior.appExitInfoMaxNum()

        var historicalProcessExitReasons: List<ApplicationExitInfo> =
            activityManager?.getHistoricalProcessExitReasons(null, pid, maxNum)
                ?: return emptyList()

        if (historicalProcessExitReasons.size > SDK_AEI_SEND_LIMIT) {
            logInfoWithException("AEI - size greater than $SDK_AEI_SEND_LIMIT")
            historicalProcessExitReasons = historicalProcessExitReasons.take(SDK_AEI_SEND_LIMIT)
        }

        return historicalProcessExitReasons
    }

    private fun getUnsentExitReasons(historicalProcessExitReasons: List<ApplicationExitInfo>): List<ApplicationExitInfo> {
        // Generates the set of current aei captured
        val allAeiHashCodes = historicalProcessExitReasons.map(::generateUniqueHash).toSet()

        // Get hash codes that were previously delivered
        val deliveredHashCodes = preferencesService.applicationExitInfoHistory ?: emptySet()

        // Subtracts aei hashcodes of already sent information to get new entries
        val unsentHashCodes = allAeiHashCodes.subtract(deliveredHashCodes)

        // Updates preferences with the new set of hashcodes
        preferencesService.applicationExitInfoHistory = allAeiHashCodes

        // Get AEI objects that were not sent
        val unsentAeiObjects = historicalProcessExitReasons.filter {
            unsentHashCodes.contains(generateUniqueHash(it))
        }

        return unsentAeiObjects
    }

    fun buildSessionAppExitInfoData(
        appExitInfo: ApplicationExitInfo,
        trace: String?,
        traceStatus: String?
    ): AppExitInfoData {
        val sessionId = String(appExitInfo.processStateSummary ?: ByteArray(0))

        return AppExitInfoData(
            sessionId = sessionId,
            sessionIdError = getSessionIdValidationError(sessionId),
            importance = appExitInfo.importance,
            pss = appExitInfo.pss,
            reason = appExitInfo.reason,
            rss = appExitInfo.rss,
            status = appExitInfo.status,
            timestamp = appExitInfo.timestamp,
            trace = trace,
            description = appExitInfo.description,
            traceStatus = traceStatus
        )
    }

    private fun getTrace(traceResult: AppExitInfoBehavior.CollectTracesResult): String? {
        return when (traceResult) {
            is AppExitInfoBehavior.CollectTracesResult.Success -> traceResult.result
            is AppExitInfoBehavior.CollectTracesResult.TooLarge -> traceResult.result
            else -> null
        }
    }

    private fun getTraceStatus(traceResult: AppExitInfoBehavior.CollectTracesResult): String? {
        return when (traceResult) {
            is AppExitInfoBehavior.CollectTracesResult.Success -> null
            is AppExitInfoBehavior.CollectTracesResult.TooLarge -> "Trace was too large, sending truncated trace"
            else -> traceResult.result
        }
    }

    private fun sendApplicationExitInfoWithTraces(appExitInfoWithTraces: List<AppExitInfoData>) {
        if (appExitInfoWithTraces.isNotEmpty()) {
            val blob = BlobMessage(
                metadataService.getAppInfo(),
                appExitInfoWithTraces,
                metadataService.getDeviceInfo(),
                BlobSession(metadataService.activeSessionId),
                userService.getUserInfo()
            )
            deliveryService.sendAEIBlob(blob)
        }
    }

    private fun collectExitInfoTrace(appExitInfo: ApplicationExitInfo): AppExitInfoBehavior.CollectTracesResult? {
        try {
            val trace = readTraceAsString(appExitInfo)

            if (trace == null) {
                logDebug("AEI - No info trace collected")
                return null
            }

            val traceMaxLimit = configService.appExitInfoBehavior.getTraceMaxLimit()
            if (trace.length > traceMaxLimit) {
                logInfoWithException("AEI - Blob size was reduced. Current size is ${trace.length} and the limit is $traceMaxLimit")
                return AppExitInfoBehavior.CollectTracesResult.TooLarge(trace.take(traceMaxLimit))
            }

            return AppExitInfoBehavior.CollectTracesResult.Success(trace)
        } catch (e: IOException) {
            logWarningWithException("AEI - IOException: ${e.message}", e, true)
            return AppExitInfoBehavior.CollectTracesResult.TraceException(("ioexception: ${e.message}"))
        } catch (e: OutOfMemoryError) {
            logWarningWithException("AEI - Out of Memory: ${e.message}", e, true)
            return AppExitInfoBehavior.CollectTracesResult.TraceException(("oom: ${e.message}"))
        } catch (tr: Throwable) {
            logWarningWithException("AEI - An error occurred: ${tr.message}", tr, true)
            return AppExitInfoBehavior.CollectTracesResult.TraceException(("error: ${tr.message}"))
        }
    }

    private fun readTraceAsString(appExitInfo: ApplicationExitInfo): String? {
        if (appExitInfo.isNdkProtobufFile()) {
            val bytes = appExitInfo.traceInputStream?.readBytes()

            if (bytes == null) {
                logDebug("AEI - No info trace collected")
                return null
            }
            return bytesToUTF8String(bytes)
        } else {
            return appExitInfo.traceInputStream?.bufferedReader()?.readText()
        }
    }

    /**
     * NDK protobuf files are only available on Android 12 and above for AEI with
     * the REASON_CRASH_NATIVE reason.
     */
    private fun ApplicationExitInfo.isNdkProtobufFile(): Boolean {
        return buildVersionChecker.isAtLeast(VERSION_CODES.S) && reason == ApplicationExitInfo.REASON_CRASH_NATIVE
    }

    /**
     * Converts a byte array to a UTF-8 string, escaping non-encodable bytes as
     * 2-byte UTF-8 sequences, which will later be converted into unicode by JSON marshalling.
     * This allows us to send arbitrary binary data from the NDK
     * protobuf file without needing to encode it as Base64 (which compresses poorly).
     */
    private fun bytesToUTF8String(bytes: ByteArray): String {
        val encoded = ByteArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            if (u < 128) {
                encoded[i++] = u.toByte()
                continue
            }
            encoded[i++] = (0xC0 or (u shr 6)).toByte()
            encoded[i++] = (0x80 or (u and 0x3F)).toByte()
        }
        return String(encoded.copyOf(i), Charsets.UTF_8)
    }

    private fun getSessionIdValidationError(sid: String): String {
        return if (sid.isEmpty() || sid.matches(Regex("^[0-9a-fA-F]{32}\$"))) {
            ""
        } else {
            "invalid session ID: $sid"
        }
    }

    private fun generateUniqueHash(appExitInfo: ApplicationExitInfo): String {
        return "${appExitInfo.timestamp}_${appExitInfo.pid}"
    }

    override fun cleanCollections() {
    }

    override fun getCapturedData() =
        sessionApplicationExitInfoData.takeIf { isSessionApplicationExitInfoDataReady.get() }
            ?: emptyList()

    override fun onConfigChange(configService: ConfigService) {
        if (backgroundExecution == null && configService.isAppExitInfoCaptureEnabled()) {
            startService()
        } else if (!configService.isAppExitInfoCaptureEnabled()) {
            endService()
        }
    }

    private fun endService() {
        try {
            backgroundExecution?.cancel(true)
            backgroundExecution = null
        } catch (t: Throwable) {
            logWarningWithException(
                "AEI - Failed to disable EmbraceApplicationExitInfoService work",
                t
            )
        }
    }
}
