package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface

internal class NoopFlutterInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : FlutterInternalInterface, EmbraceInternalInterface by delegate {

    override fun setEmbraceFlutterSdkVersion(version: String?) {
    }

    override fun setDartVersion(version: String?) {
    }
}
