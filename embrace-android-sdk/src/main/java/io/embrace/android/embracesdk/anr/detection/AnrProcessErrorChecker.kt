package io.embrace.android.embracesdk.anr.detection

import android.app.ActivityManager
import android.os.Process
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
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
@JsonClass(generateAdapter = true)
internal data class AnrProcessErrorStateInfo(

    /**
     * The activity name associated with the error, if known.  May be null.
     */
    @Json(name = "t")
    val tag: String? = null,

    /**
     * A short message describing the error condition.
     */
    @Json(name = "sm")
    val shortMsg: String? = null,

    /**
     * A long message describing the error condition.
     */
    @Json(name = "lm")
    val longMsg: String? = null,

    /**
     * The stack trace where the error originated. May be null.
     */
    @Json(name = "st")
    val stackTrace: String? = null,

    /**
     * The timestamp where the process error info was first detected.
     */
    @Json(name = "ts")
    val timestamp: Long? = null,
)
