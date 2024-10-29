package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

internal class NoopFlutterInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : FlutterInternalInterface, EmbraceInternalInterface by delegate {

    override fun setEmbraceFlutterSdkVersion(version: String?) {
    }

    override fun setDartVersion(version: String?) {
    }

    override fun logHandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?,
    ) {
    }

    override fun logUnhandledDartException(
        stack: String?,
        name: String?,
        message: String?,
        context: String?,
        library: String?,
    ) {
    }
}
