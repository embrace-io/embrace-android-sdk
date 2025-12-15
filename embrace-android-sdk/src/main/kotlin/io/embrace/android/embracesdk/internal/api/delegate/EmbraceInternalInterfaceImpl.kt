package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource

internal class EmbraceInternalInterfaceImpl(
    private val configService: ConfigService,
    private val resourceSource: EnvelopeResourceSource,
) : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()

    override fun addEnvelopeResource(key: String, value: String) {
        resourceSource.add(key, value)
    }
}
