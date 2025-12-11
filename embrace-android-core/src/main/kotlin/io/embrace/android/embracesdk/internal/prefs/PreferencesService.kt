package io.embrace.android.embracesdk.internal.prefs

interface PreferencesService {

    /**
     * The unique identifier for this device.
     */
    var deviceIdentifier: String

    /**
     * Last javaScript bundle string url.
     */
    var javaScriptBundleURL: String?

    /**
     * Last javaScript bundle ID.
     */
    var javaScriptBundleId: String?
}
