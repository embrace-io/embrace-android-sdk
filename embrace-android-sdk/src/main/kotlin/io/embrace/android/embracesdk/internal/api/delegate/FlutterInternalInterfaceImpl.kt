package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalLogger

internal class FlutterInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: InternalLogger,
) : EmbraceInternalInterface by impl, FlutterInternalInterface {

    override fun setEmbraceFlutterSdkVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                hostedSdkVersionInfo.hostedSdkVersion = version
            }
        } else {
            logger.logSdkNotInitialized("setEmbraceFlutterSdkVersion")
        }
    }

    override fun setDartVersion(version: String?) {
        if (embrace.isStarted) {
            if (version != null) {
                hostedSdkVersionInfo.hostedPlatformVersion = version
            }
        } else {
            logger.logSdkNotInitialized("setDartVersion")
        }
    }
}
