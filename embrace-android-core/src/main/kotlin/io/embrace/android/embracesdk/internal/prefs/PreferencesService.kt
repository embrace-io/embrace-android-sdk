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
     * The unique identifier for this device.
     */
    var deviceIdentifier: String

    /**
     * All permanent session properties
     */
    var permanentSessionProperties: Map<String, String>?

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

    companion object {
        const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
