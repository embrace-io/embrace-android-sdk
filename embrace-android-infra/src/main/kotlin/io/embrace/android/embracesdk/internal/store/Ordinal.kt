package io.embrace.android.embracesdk.internal.store

enum class Ordinal(val key: String) {

    /**
     * Increments on every crash. It allows us to check the % of crashes that
     * didn't get delivered to the backend.
     */
    CRASH("io.embrace.crashnumber"),

    /**
     * Increments on every native crash. It allows us to check the % of native crashes that
     * didn't get delivered to the backend.
     */
    NATIVE_CRASH("io.embrace.nativecrashnumber"),

    /**
     * Increments on every AEI crash. It allows us to check the % of AEI crashes that
     * didn't get delivered to the backend.
     */
    AEI_CRASH("io.embrace.aeicrashnumber"),

    /**
     * Increments at the start of every session. This allows us to check the % of sessions
     * that didn't get delivered to the backend.
     */
    SESSION("io.embrace.sessionnumber"),

    /**
     * Increments at the start of every background activity. This allows us to check
     * the % of background activities that didn't get delivered to the backend.
     */
    BACKGROUND_ACTIVITY("io.embrace.bgactivitynumber")
}
