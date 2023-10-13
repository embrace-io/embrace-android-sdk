package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Holds data for a sample of a native stackframe.
 * IMPORTANT: This class is referenced by stacktrace_sampler_jni.c. Move or rename both at the same time, or it will break.
 */
internal class NativeThreadAnrSample(

    /**
     * A zero value indicates the sample was successful. A non-zero value indicates
     * that something went wrong with the sample. Error codes match those defined in utilities.h.
     *
     * Depending on the error code, the stack might not be populated if the error condition is
     * likely to increase the payload size.
     */
    @SerializedName("r")
    val result: Int?,

    /**
     * The time in milliseconds since the thread was first detected as blocked
     */
    @SerializedName("t")
    val sampleTimestamp: Long?,

    /**
     * How long the sample took in milliseconds.
     */
    @SerializedName("d")
    val sampleDurationMs: Long?,

    /**
     * All the stackframes which have been captured during the current sample.
     */
    @SerializedName("s")
    val stackframes: List<NativeThreadAnrStackframe>?
)
