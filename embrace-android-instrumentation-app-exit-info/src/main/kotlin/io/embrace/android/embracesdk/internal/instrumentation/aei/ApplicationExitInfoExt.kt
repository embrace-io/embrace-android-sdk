package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.instrumentation.aei.TraceResult.Failure
import io.embrace.android.embracesdk.internal.instrumentation.aei.TraceResult.Success
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.util.regex.Pattern

private val SESSION_ID_PATTERN by lazy { Pattern.compile("^[0-9a-fA-F]{32}\$").toRegex() }
private const val SESSION_ID_LENGTH = 32
private const val READ_BUFFER_SIZE = 8192

/**
 * Constructs an [io.embrace.android.embracesdk.internal.payload.AppExitInfoData] object from an [ApplicationExitInfo] object. The trace
 * will be truncated if it is above a certain character limit. If no trace is present, this function
 * will return null as we don't wish to capture this data right now.
 */
@RequiresApi(VERSION_CODES.R)
internal fun ApplicationExitInfo.constructAeiObject(
    versionChecker: VersionChecker,
    charLimit: Int,
): AppExitInfoData? {
    val result = readAeiTrace(versionChecker, charLimit) ?: return null
    val summary = String(processStateSummary ?: ByteArray(0))
    val parsed = parseProcessStateSummary(summary)

    return AppExitInfoData(
        sessionPartId = parsed.sessionPartId,
        userSessionId = parsed.userSessionId,
        sessionIdError = when {
            parsed.valid || summary.isEmpty() || summary.matches(SESSION_ID_PATTERN) -> ""
            else -> "invalid session ID: $summary"
        },
        importance = importance,
        pss = pss,
        reason = reason,
        rss = rss,
        status = status,
        timestamp = timestamp,
        trace = result.trace,
        description = description,
        traceStatus = result.errMsg,
        pid = pid,
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
        val trace = readTraceAsString(versionChecker, charLimit) ?: return null

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
private fun ApplicationExitInfo.readTraceAsString(versionChecker: VersionChecker, charLimit: Int): String? {
    val stream = traceInputStream ?: return null
    return if (isNdkProtobufFile(versionChecker)) {
        stream.use { it.readBytesCapped(charLimit).toUTF8String() }
    } else {
        stream.bufferedReader().use { it.readTextCapped(charLimit) }
    }
}

/**
 * Reads at most one buffer beyond [charLimit] bytes, so oversized traces can be detected
 * and truncated without ever reading the entire source into memory.
 */
private fun InputStream.readBytesCapped(charLimit: Int): ByteArray {
    val out = ByteArrayOutputStream(READ_BUFFER_SIZE)
    val buffer = ByteArray(READ_BUFFER_SIZE)
    while (out.size() <= charLimit) {
        val count = read(buffer)
        if (count == -1) {
            break
        }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

/**
 * Reads at most one buffer beyond [charLimit] chars, so oversized traces can be detected
 * and truncated without ever reading the entire source into memory.
 */
private fun Reader.readTextCapped(charLimit: Int): String {
    val sb = StringBuilder(READ_BUFFER_SIZE)
    val buffer = CharArray(READ_BUFFER_SIZE)
    while (sb.length <= charLimit) {
        val count = read(buffer)
        if (count == -1) {
            break
        }
        sb.append(buffer, 0, count)
    }
    return sb.toString()
}

/**
 * NDK protobuf files are only available on Android 12 and above for AEI with
 * the REASON_CRASH_NATIVE reason.
 */
private fun ApplicationExitInfo.isNdkProtobufFile(versionChecker: VersionChecker): Boolean {
    return versionChecker.isAtLeast(VERSION_CODES.S) && reason == ApplicationExitInfo.REASON_CRASH_NATIVE
}

/**
 * Parses the processStateSummary string. If it has the combined format
 * `{sessionPartId}_{userSessionId}` (both 32-char hex IDs delimited by `_`), both are
 * returned. Otherwise the entire string is treated as the session part ID with no user session ID.
 */
internal fun parseProcessStateSummary(summary: String): ProcessStateSummary {
    val expectedLength = SESSION_ID_LENGTH * 2 + 1
    return if (summary.length == expectedLength && summary[SESSION_ID_LENGTH] == '_') {
        ProcessStateSummary(
            sessionPartId = summary.substring(0, SESSION_ID_LENGTH),
            userSessionId = summary.substring(SESSION_ID_LENGTH + 1),
        )
    } else {
        ProcessStateSummary(sessionPartId = "", userSessionId = "")
    }
}
