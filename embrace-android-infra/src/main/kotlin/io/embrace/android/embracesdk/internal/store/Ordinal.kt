package io.embrace.android.embracesdk.internal.store

enum class Ordinal(
    /**
     * The key under which the ordinal's current value is persisted.
     */
    val key: String,

    /**
     * The key under which the ordinal's current scope is persisted, for ordinals whose value
     * resets when the scope changes. Null for ordinals that don't support scoping.
     */
    val scopeKey: String? = null,
) {

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
    BACKGROUND_ACTIVITY("io.embrace.bgactivitynumber"),

    /**
     * Increments at the start of every user session. This allows us to check the % of user sessions
     * that didn't get delivered to the backend.
     */
    USER_SESSION("io.embrace.usersessionnumber"),

    /**
     * Monotonically increments on each session part created. Persisted across SDK install
     * lifetime; seeded from [USER_SESSION] the first time it is read. Surfaced as
     * `emb.session_part_number`.
     */
    SESSION_PART("io.embrace.sessionpartnumber"),

    /**
     * Increments every time the SDK begins startup in a new app process, even if startup never
     * completes, and resets when the app version changes, so it counts the number of SDK startups
     * on the current app version. Surfaced as `emb.app.version_startup_counter`.
     */
    APP_VERSION_STARTUP(
        key = "io.embrace.appversionstartupcounter",
        scopeKey = "io.embrace.appversionstartupcounterscope",
    ),
}
