package io.embrace.android.embracesdk.semconv

@RequiresOptIn(
    message = "This Embrace semantic convention is experimental and subject to change in future releases.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
annotation class ExperimentalSemconv
