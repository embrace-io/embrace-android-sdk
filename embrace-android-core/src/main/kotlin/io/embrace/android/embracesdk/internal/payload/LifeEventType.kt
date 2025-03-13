package io.embrace.android.embracesdk.internal.payload

/**
 * Enum to discriminate the different ways a session can start / end
 */
enum class LifeEventType {

    /* Session values */
    STATE,
    MANUAL,

    /* Background activity values */
    BKGND_STATE,
    BKGND_MANUAL
}
