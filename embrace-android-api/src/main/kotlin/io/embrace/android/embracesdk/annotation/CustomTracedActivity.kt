package io.embrace.android.embracesdk.annotation

/**
 * The loading of Activities annotated with this class will generate traces if the feature is enabled, irrespective of
 * the configured defaults.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CustomTracedActivity
