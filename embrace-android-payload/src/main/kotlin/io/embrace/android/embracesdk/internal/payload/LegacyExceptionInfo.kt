package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes a particular Java exception. Where an exception has a cause, there will be an
 * [LegacyExceptionInfo] for each nested cause.
 */
@Serializable
@JsonClass(generateAdapter = true)
class LegacyExceptionInfo internal constructor(

    /**
     * The name of the class throwing the exception.
     */
    @SerialName("n") @Json(name = "n") val name: String,

    /**
     * The exception message.
     */
    @SerialName("m") @Json(name = "m") val message: String?,

    /**
     * String representation of each line of the stack trace.
     */
    @SerialName("tt") @Json(name = "tt") val lines: List<String>,

    /**
     * The original length of the stack trace. This will be null if it has not been truncated.
     */
    @SerialName("length") @Json(name = "length") val originalLength: Int?,
) {

    /**
     * Creates a [LegacyExceptionInfo], truncating the stack trace to at most [STACK_FRAME_LIMIT]
     * frames and recording the original length when truncation occurs.
     */
    constructor(name: String, message: String?, lines: List<String>) : this(
        name = name,
        message = message,
        lines = lines.take(STACK_FRAME_LIMIT),
        originalLength = lines.size.takeIf { it > STACK_FRAME_LIMIT },
    )

    companion object {

        /**
         * Maximum number of stackframes we are interested in serializing.
         */
        private const val STACK_FRAME_LIMIT = 200

        /**
         * Creates a [LegacyExceptionInfo] from a [Throwable], using the classname as the name,
         * the exception message as the message, and each stacktrace element as each line.
         *
         * @param throwable the exception
         * @return the stacktrace instance
         */
        fun ofThrowable(throwable: Throwable): LegacyExceptionInfo {
            val name = throwable.javaClass.name
            val message = throwable.message ?: ""
            val lines = throwable.stackTrace.map(StackTraceElement::toString)
            return LegacyExceptionInfo(name, message, lines)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LegacyExceptionInfo

        if (name != other.name) return false
        if (message != other.message) return false
        if (lines != other.lines) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + lines.hashCode()
        return result
    }
}
