package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.CoreConfigService
import io.embrace.android.embracesdk.config.behavior.SdkAppBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior

/**
 * Fake [CoreConfigService] used for testing.
 */
internal class FakeCoreConfigService(
    override val sdkAppBehavior: SdkAppBehavior = fakeSdkAppBehavior(),
    override val sdkEndpointBehavior: SdkEndpointBehavior = fakeSdkEndpointBehavior(),
) : CoreConfigService {
    override fun close() {}
}
