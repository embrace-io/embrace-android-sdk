package io.embrace.android.embracesdk.annotation

/**
 * The loading of Activities annotated with this will generate UI Load traces when the SDK is manually notified
 * that their loading has finished unless the feature is disabled.
 *
 * This works similarly to [LoadTracedActivity] except the trace will only be recorded if the appropriate API method
 * is called to signal the end of the load.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class CustomLoadTracedActivity
