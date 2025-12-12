package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.config.ConfigService

internal class EmbraceInternalInterfaceImpl(
    private val configService: ConfigService,
) : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()
}
