package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

class NetworkStatusDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATUS }
) {
    private companion object {
        private const val MAX_CAPTURED_NETWORK_STATUS = 100
    }

    private var span: SpanToken? = null
    private var state: SpanToken? = null
    private var currentState: NetworkStatus = NetworkStatus.UNKNOWN
    private var transitionCount = 0

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        // close previous span
        val timestamp = clock.now()
        span?.stop(endTimeMs = timestamp)
        // start a new span with the new network status
        captureTelemetry {
            span = startSpanCapture(SchemaType.NetworkStatus(status.value), timestamp)
        }
        stateChange(status, timestamp)
        currentState = status
    }

    private fun stateChange(newState: NetworkStatus, timestamp: Long) {
        captureTelemetry {
            val stateSpan = state
            if (stateSpan == null) {
                transitionCount = 0
                state = startSpanCapture(
                    schemaType = SchemaType.NetworkState(currentState.value),
                    startTimeMs = timestamp,
                    autoTerminate = true
                )
            } else if (newState != currentState) {
                transitionCount++
                stateSpan.addEvent(
                    name = "state_transition",
                    eventTimeMs = timestamp,
                    attributes = mapOf(
                        "new_state" to newState.value,
                        "transition_count" to transitionCount.toString()
                    )
                )
            }
        }
    }
}
