package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

@Suppress("LongParameterList")
class FakePreferenceService(
    override var appVersion: String? = null,
    override var osVersion: String? = null,
    override var installDate: Long? = 0,
    override var deviceIdentifier: String = "",
    override var userPayer: Boolean = false,
    override var userIdentifier: String? = null,
    override var userEmailAddress: String? = null,
    override var userPersonas: Set<String>? = null,
    override var username: String? = null,
    override var permanentSessionProperties: Map<String, String>? = null,
    override var lastConfigFetchDate: Long? = null,
    override var javaScriptBundleURL: String? = null,
    override var javaScriptBundleId: String? = null,
    val sessionNumber: () -> Int = { 0 },
    val bgActivityNumber: () -> Int = { 5 },
) : PreferencesService {

    var firstDay: Boolean = false
    var incrementAndGetSessionNumberCount: Int = 0

    override fun incrementAndGetSessionNumber(): Int {
        incrementAndGetSessionNumberCount++
        return sessionNumber()
    }

    override fun incrementAndGetBackgroundActivityNumber(): Int = bgActivityNumber()

    override fun incrementAndGetCrashNumber(): Int {
        return 1
    }

    override fun incrementAndGetNativeCrashNumber(): Int {
        return 1
    }

    override fun isUsersFirstDay(): Boolean = firstDay
}
