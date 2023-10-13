package io.embrace.android.embracesdk.payload

import android.util.Base64
import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logError
import io.embrace.android.embracesdk.payload.ExceptionInfo.Companion.ofThrowable
import io.embrace.android.embracesdk.payload.ThreadInfo.Companion.ofThread

internal data class Crash(
    @SerializedName("id")
    @JvmField
    val crashId: String,

    @SerializedName("ex")
    val exceptions: List<ExceptionInfo>? = null,

    @SerializedName("rep_js")
    val jsExceptions: List<String>? = null,

    @SerializedName("th")
    val threads: List<ThreadInfo>? = null
) {

    companion object {
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
            throwable: Throwable?,
            jsException: JsException?,
            crashId: String = Uuid.getEmbUuid()
        ): Crash {
            return Crash(
                crashId,
                exceptionInfo(throwable),
                jsExceptions(jsException),
                threadsInfo()
            )
        }

        /**
         * @param ex the throwable to parse
         * @return a list of [ExceptionInfo] elements of the throwable.
         */
        @JvmStatic
        private fun exceptionInfo(ex: Throwable?): List<ExceptionInfo> {
            val result = mutableListOf<ExceptionInfo>()
            var throwable: Throwable? = ex
            while (throwable != null && throwable != throwable.cause) {
                val exceptionInfo = ofThrowable(throwable)
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
            return Thread.getAllStackTraces().map { ofThread(it.key, it.value) }
        }

        /**
         * @param jsException the [JsException] coming from the React Native layer.
         * @return a list of [String] representing the javascript stacktrace of the crash.
         */
        @JvmStatic
        private fun jsExceptions(jsException: JsException?): List<String>? {
            var jsExceptions: List<String>? = null
            if (jsException != null) {
                try {
                    val jsonException = serializer.toJson(jsException, jsException.javaClass).toByteArray()
                    val encodedString = Base64.encodeToString(jsonException, Base64.NO_WRAP)
                    jsExceptions = listOf(encodedString)
                } catch (ex: Exception) {
                    logError("Failed to parse javascript exception", ex, true)
                }
            }
            return jsExceptions
        }
    }
}
