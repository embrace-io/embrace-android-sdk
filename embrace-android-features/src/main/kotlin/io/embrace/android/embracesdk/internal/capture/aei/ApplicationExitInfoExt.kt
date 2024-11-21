package io.embrace.android.embracesdk.internal.capture.aei

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.capture.aei.TraceResult.Failure
import io.embrace.android.embracesdk.internal.capture.aei.TraceResult.Success
import io.embrace.android.embracesdk.internal.payload.AppExitInfoData
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import java.io.IOException
import java.util.regex.Pattern

private val SESSION_ID_PATTERN by lazy { Pattern.compile("^[0-9a-fA-F]{32}\$").toRegex() }

/**
 * Constructs an [AppExitInfoData] object from an [ApplicationExitInfo] object. The trace
 * will be truncated if it is above a certain character limit. If no trace is present, this function
 * will return null as we don't wish to capture this data right now.
 */
@RequiresApi(VERSION_CODES.R)
internal fun ApplicationExitInfo.constructAeiObject(
    versionChecker: VersionChecker,
    charLimit: Int,
): AppExitInfoData? {
    val result = readAeiTrace(versionChecker, charLimit) ?: return null
    val sessionId = String(processStateSummary ?: ByteArray(0))

    return AppExitInfoData(
        sessionId = sessionId,
        sessionIdError = getSessionIdValidationError(sessionId),
        importance = importance,
        pss = pss,
        reason = reason,
        rss = rss,
        status = status,
        timestamp = timestamp,
        trace = result.trace,
        description = description,
        traceStatus = result.errMsg,
    )
}

/**
 * Reads an AEI trace as a string and attempts to sanitize/handle exceptions + edge cases.
 */
@RequiresApi(VERSION_CODES.R)
private fun ApplicationExitInfo.readAeiTrace(
    versionChecker: VersionChecker,
    charLimit: Int,
): TraceResult? {
    return try {
        val trace = readTraceAsString(versionChecker) ?: return null

        when {
            trace.length > charLimit -> Success(trace.take(charLimit), "Trace was too large, sending truncated trace")
            else -> Success(trace)
        }
    } catch (e: IOException) {
        Failure("ioexception: ${e.message}")
    } catch (e: OutOfMemoryError) {
        Failure("oom: ${e.message}")
    } catch (tr: Throwable) {
        Failure("error: ${tr.message}")
    }
}

@RequiresApi(VERSION_CODES.R)
private fun ApplicationExitInfo.readTraceAsString(versionChecker: VersionChecker): String? {
    return if (isNdkProtobufFile(versionChecker)) {
        val bytes = traceInputStream?.buffered()?.readBytes() ?: return null
        bytes.toUTF8String()
    } else {
        traceInputStream?.bufferedReader()?.readText()
    }
}

/**
 * NDK protobuf files are only available on Android 12 and above for AEI with
 * the REASON_CRASH_NATIVE reason.
 */
private fun ApplicationExitInfo.isNdkProtobufFile(versionChecker: VersionChecker): Boolean {
    return versionChecker.isAtLeast(VERSION_CODES.S) && reason == ApplicationExitInfo.REASON_CRASH_NATIVE
}

private fun getSessionIdValidationError(sid: String): String = when {
    sid.isEmpty() || sid.matches(SESSION_ID_PATTERN) -> ""
    else -> "invalid session ID: $sid"
}
