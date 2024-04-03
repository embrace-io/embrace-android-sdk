package io.embrace.android.embracesdk.logging

/**
 * A version of the logger used when the SDK instance is not readily available
 */
internal object InternalStaticEmbraceLogger {
    @JvmField
    val logger = InternalEmbraceLogger()
}
