package io.embrace.android.embracesdk.annotation

/**
 * The loading of Activities annotated with this will generate UI Load traces unless the feature is disabled.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class LoadTracedActivity
