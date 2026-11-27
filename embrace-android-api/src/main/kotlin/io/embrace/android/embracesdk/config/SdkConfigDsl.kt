package io.embrace.android.embracesdk.config

/**
 * Marks the receivers of the configuration DSL passed to `Embrace.start()`, so that a nested block can't implicitly call
 * functions of an enclosing block's receiver.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class SdkConfigDsl
