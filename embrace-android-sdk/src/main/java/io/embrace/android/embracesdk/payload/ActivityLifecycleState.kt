package io.embrace.android.embracesdk.payload

/**
 * The possible Activity lifecycle states that we care about capturing
 */
internal enum class ActivityLifecycleState {
    ON_CREATE,
    ON_START,
    ON_RESUME,
    ON_PAUSE,
    ON_STOP,
    ON_DESTROY,
    ON_SAVE_INSTANCE_STATE,
}
