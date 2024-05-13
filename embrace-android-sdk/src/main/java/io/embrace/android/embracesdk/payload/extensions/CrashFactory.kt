package io.embrace.android.embracesdk.payload.extensions

import android.util.Base64
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.InternalErrorType
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.payload.ThreadInfo

internal object CrashFactory {

    private val serializer = EmbraceSerializer()

    /**
     * Creates a crash from a {@link Throwable}. Extracts each cause and converts it to
     * {@link ExceptionInfo}. Optionally includes a {@link JsException}.
     *
     * @param throwable   the throwable to parse
     * @param jsException an optional JS exception that is associated with the crash
     * @param crashId     an optional crash unique id
     * @return a crash
     */
    fun ofThrowable(
        logger: EmbLogger,
        throwable: Throwable?,
        jsException: JsException?,
        crashNumber: Int,
        crashId: String = Uuid.getEmbUuid()
    ): Crash {
        return Crash(
            crashId,
            exceptionInfo(throwable),
            jsExceptions(jsException, logger),
            threadsInfo(),
            crashNumber
        )
    }

    /**
     * @param ex the throwable to parse
     * @return a list of [LegacyExceptionInfo] elements of the throwable.
     */
    @JvmStatic
    private fun exceptionInfo(ex: Throwable?): List<LegacyExceptionInfo> {
        val result = mutableListOf<LegacyExceptionInfo>()
        var throwable: Throwable? = ex
        while (throwable != null && throwable != throwable.cause) {
            val exceptionInfo = LegacyExceptionInfo.ofThrowable(throwable)
            result.add(0, exceptionInfo)
            throwable = throwable.cause
        }
        return result.toList()
    }

    /**
     * @return a list of [ThreadInfo] elements of the current thread list.
     */
    @JvmStatic
    private fun threadsInfo(): List<ThreadInfo> {
        return Thread.getAllStackTraces().map { ThreadInfo.ofThread(it.key, it.value) }
    }

    /**
     * @param jsException the [JsException] coming from the React Native layer.
     * @return a list of [String] representing the javascript stacktrace of the crash.
     */
    @JvmStatic
    private fun jsExceptions(jsException: JsException?, logger: EmbLogger): List<String>? {
        var jsExceptions: List<String>? = null
        if (jsException != null) {
            try {
                val jsonException = serializer.toJson(jsException).toByteArray()
                val encodedString = Base64.encodeToString(jsonException, Base64.NO_WRAP)
                jsExceptions = listOf(encodedString)
            } catch (ex: Exception) {
                logger.logError(
                    "Failed to parse javascript exception",
                    ex
                )
                logger.trackInternalError(InternalErrorType.INVALID_JS_EXCEPTION, ex)
            }
        }
        return jsExceptions
    }
}
