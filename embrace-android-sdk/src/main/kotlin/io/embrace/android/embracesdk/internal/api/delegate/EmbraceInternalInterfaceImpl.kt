package io.embrace.android.embracesdk.internal.api.delegate

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceInternalInterfaceImpl(
    private val embraceImpl: EmbraceImpl,
    private val initModule: InitModule,
    private val networkCaptureDataSourceProvider: () -> NetworkCaptureDataSource?,
    private val configService: ConfigService,
    internalTracer: InternalTracer,
) : EmbraceInternalInterface, InternalTracingApi by internalTracer {

    override fun isNetworkSpanForwardingEnabled(): Boolean =
        configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean {
        return networkCaptureDataSourceProvider()?.shouldCaptureNetworkBody(url, method) == true
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        embraceImpl.recordNetworkRequest(networkRequest)
    }

    override fun logInternalError(message: String?, details: String?) {
        if (message == null) {
            return
        }
        val messageWithDetails: String = if (details != null) {
            "$message: $details"
        } else {
            message
        }
        initModule.logger.trackInternalError(
            InternalErrorType.INTERNAL_INTERFACE_FAIL,
            RuntimeException(messageWithDetails)
        )
    }

    override fun logInternalError(error: Throwable) {
        initModule.logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, error)
    }
}
