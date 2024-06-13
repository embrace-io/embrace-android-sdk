package io.embrace.android.embracesdk.payload

/**
 * Holds data for a sample of a native stacktrace.
 * IMPORTANT: This class is referenced by stacktrace_sampler_jni.c. Move or rename both at the same time, or it will break.
 */
internal data class NativeThreadAnrStackframe(

    /**
     * The program counter
     */
    internal val pc: String?,

    /**
     * The hex load address of shared object. This information may not be available
     * in which case the value will be 0x0.
     */
    internal val soLoadAddr: String?,

    /**
     * The absolute path of the shared object. This information may not be available
     * in which case the string will be null.
     */
    internal val soPath: String?,

    /**
     * The result for unwinding this particular stackframe. Non-zero values indicate an error.
     */
    internal val result: Int?
)
