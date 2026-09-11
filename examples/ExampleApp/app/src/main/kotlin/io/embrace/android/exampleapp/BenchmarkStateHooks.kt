package io.embrace.android.exampleapp

import android.content.Context
import android.provider.Settings

/**
 * Device-state hooks for the startup benchmarks. Each is armed from the shell through a
 * `Settings.Global` key (readable by any app, writable by `adb shell settings put global`), so a
 * benchmark's `setupBlock` can put the app into a production-like state without root, `run-as`
 * (unavailable on a release build) or `pm clear` (which also wipes the persisted config).
 *
 * Nothing here runs unless the key is set; an unarmed app behaves exactly like production.
 */
object BenchmarkStateHooks {

    /**
     * Forces the next SDK start onto the "new user session" path by deleting the persisted user
     * session from the app's default SharedPreferences (the file the SDK's key-value store is
     * backed by) BEFORE `Embrace.start`. The SDK restores a stored session that is within its
     * 30-minute inactivity timeout, which every benchmark relaunch is; a production cold start is
     * usually hours after the previous one and creates a new session instead. That create path
     * serializes and writes the metadata on the main thread inside `start-first-session`, and is
     * what production's `post-init` measures.
     *
     * Cost to the measurement: this loads the default prefs file on the main thread before the SDK's
     * own prewarm, so `key-value-store-init` / `prefs-first-read` read a few hundred microseconds
     * lower than in an unarmed launch. `start-first-session` is unaffected.
     *
     * Arm with `settings put global embrace_bench_expire_user_session 1`; disarm with
     * `settings delete global embrace_bench_expire_user_session`.
     */
    fun expireUserSessionIfRequested(context: Context) {
        val armed = Settings.Global.getString(context.contentResolver, EXPIRE_USER_SESSION_KEY) == "1"
        if (!armed) {
            return
        }
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .remove(SDK_USER_SESSION_PREF_KEY)
            .commit()
    }

    private const val EXPIRE_USER_SESSION_KEY = "embrace_bench_expire_user_session"

    /** `UserSessionMetadataStore.KEY_SESSION` in the SDK; internal, so pinned here by value. */
    private const val SDK_USER_SESSION_PREF_KEY = "embrace.user_session"
}
