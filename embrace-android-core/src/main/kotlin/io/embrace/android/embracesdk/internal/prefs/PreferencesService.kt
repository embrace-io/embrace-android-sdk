package io.embrace.android.embracesdk.internal.prefs

interface PreferencesService {

    /**
     * The last registered Host App version name
     */
    var appVersion: String?

    /**
     * The last registered OS Version
     */
    var osVersion: String?

    /**
     * The app install date in ms
     */
    var installDate: Long?

    /**
     * The unique identifier for this device.
     */
    var deviceIdentifier: String

    /**
     * If the user is payer
     */
    var userPayer: Boolean

    /**
     * User unique identifier
     */
    var userIdentifier: String?

    /**
     * User email address
     */
    var userEmailAddress: String?

    /**
     * Personas for the user
     */
    var userPersonas: Set<String>?

    /**
     * All permanent session properties
     */
    var permanentSessionProperties: Map<String, String>?

    /**
     * Username for the user
     */
    var username: String?

    /**
     * The last time config was fetched from the server
     */
    var lastConfigFetchDate: Long?

    /**
     * Last javaScript bundle string url.
     */
    var javaScriptBundleURL: String?

    /**
     * Last javaScript bundle ID.
     */
    var javaScriptBundleId: String?

    /**
     * Whether or not the app was installed within the last 24 hours.
     *
     * @return true if it is the user's first day, false otherwise
     */
    fun isUsersFirstDay(): Boolean

    companion object {
        const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
