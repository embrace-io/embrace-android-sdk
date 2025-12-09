package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

class FakePreferenceService(
    override var deviceIdentifier: String = "",
    override var javaScriptBundleURL: String? = null,
    override var javaScriptBundleId: String? = null,
) : PreferencesService
