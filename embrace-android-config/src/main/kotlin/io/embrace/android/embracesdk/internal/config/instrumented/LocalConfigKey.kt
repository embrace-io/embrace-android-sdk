package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * The embrace-config.json path whose value the Embrace Gradle plugin instruments into this getter.
 */
@Retention(AnnotationRetention.BINARY)
annotation class LocalConfigKey(val path: String)
