package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class EmbraceInternalInterfaceImpl(
    private val embraceImpl: EmbraceImpl,
    private val initModule: InitModule,
    private val networkCaptureDataSourceProvider: () -> NetworkCaptureDataSource?,
    private val configService: ConfigService,
) : EmbraceInternalInterface {

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean {
        return networkCaptureDataSourceProvider()?.shouldCaptureNetworkBody(url, method) == true
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        embraceImpl.recordNetworkRequest(networkRequest)
    }

    override fun logInternalError(error: Throwable) {
        initModule.logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, error)
    }
}
