package io.embrace.android.embracesdk.anr.detection

import android.app.ActivityManager
import android.os.Process
import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.internal.clock.Clock

private const val DATA_LIMIT_BYTES = 16 * 1024

internal fun findAnrProcessErrorStateInfo(
    clock: Clock,
    activityManager: ActivityManager?,
    pid: Int = Process.myPid()
): AnrProcessErrorStateInfo? {
    return activityManager?.processesInErrorState
        ?.filter { it.pid == pid }
        ?.filter { it.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING }
        ?.map { info ->
            AnrProcessErrorStateInfo(
                info.tag,
                info.shortMsg.take(DATA_LIMIT_BYTES),
                info.longMsg.take(DATA_LIMIT_BYTES),
                info.stackTrace.take(DATA_LIMIT_BYTES),
                clock.now()
            )
        }
        ?.singleOrNull()
}

/**
 * Holds information about the ANR as reported by [AnrProcessErrorStateInfo].
 */
internal data class AnrProcessErrorStateInfo(

    /**
     * The activity name associated with the error, if known.  May be null.
     */
    @SerializedName("t")
    val tag: String? = null,

    /**
     * A short message describing the error condition.
     */
    @SerializedName("sm")
    val shortMsg: String? = null,

    /**
     * A long message describing the error condition.
     */
    @SerializedName("lm")
    val longMsg: String? = null,

    /**
     * The stack trace where the error originated. May be null.
     */
    @SerializedName("st")
    val stackTrace: String? = null,

    /**
     * The timestamp where the process error info was first detected.
     */
    @SerializedName("ts")
    val timestamp: Long? = null,
)
