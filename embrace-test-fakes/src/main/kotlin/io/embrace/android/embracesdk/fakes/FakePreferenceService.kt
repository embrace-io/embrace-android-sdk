package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class FakePreferenceService(
    override var appVersion: String? = null,
    override var osVersion: String? = null,
    override var deviceIdentifier: String = "",
    override var permanentSessionProperties: Map<String, String>? = null,
    override var lastConfigFetchDate: Long? = null,
    override var javaScriptBundleURL: String? = null,
    override var javaScriptBundleId: String? = null,
) : PreferencesService
