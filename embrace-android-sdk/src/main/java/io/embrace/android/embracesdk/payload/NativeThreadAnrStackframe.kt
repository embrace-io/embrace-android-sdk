package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Holds data for a sample of a native stacktrace.
 * IMPORTANT: This class is referenced by stacktrace_sampler_jni.c. Move or rename both at the same time, or it will break.
 */
@JsonClass(generateAdapter = true)
internal data class NativeThreadAnrStackframe(

    /**
     * The program counter
     */
    @Json(name = "pc")
    internal val pc: String?,

    /**
     * The hex load address of shared object. This information may not be available
     * in which case the value will be 0x0.
     */
    @Json(name = "l")
    internal val soLoadAddr: String?,

    /**
     * The absolute path of the shared object. This information may not be available
     * in which case the string will be null.
     */
    @Json(name = "p")
    internal val soPath: String?,

    /**
     * The result for unwinding this particular stackframe. Non-zero values indicate an error.
     */
    @Json(name = "r")
    internal val result: Int?
)
