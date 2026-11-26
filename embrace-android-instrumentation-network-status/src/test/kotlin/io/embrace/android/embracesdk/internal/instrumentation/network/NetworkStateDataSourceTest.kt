package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class NetworkStateDataSourceTest {

    private lateinit var dataSource: NetworkStateDataSource

    @Before
    fun setUp() {
        val args = FakeInstrumentationArgs()
        dataSource = NetworkStateDataSource(args)
    }

    @Test
    fun `every ConnectivityStatus updates the current state properly`() {
        val mapping = mapOf(
            ConnectivityStatus.None to Status.NOT_REACHABLE,
            ConnectivityStatus.Unverified to Status.UNVERIFIED,
            ConnectivityStatus.Wifi(true) to Status.WIFI,
            ConnectivityStatus.Wifi(false) to Status.WIFI_CONNECTING,
            ConnectivityStatus.Wan(true) to Status.WAN,
            ConnectivityStatus.Wan(false) to Status.WAN_CONNECTING,
            ConnectivityStatus.Unknown(true) to Status.UNKNOWN,
            ConnectivityStatus.Unknown(false) to Status.UNKNOWN_CONNECTING,
        )
        mapping.forEach { (connectivityStatus, expectedState) ->
            dataSource.onNetworkConnectivityStatusChanged(connectivityStatus)
            assertEquals(
                "Expected $expectedState for $connectivityStatus",
                expectedState,
                dataSource.getCurrentStateValue(),
            )
        }
    }

    @Test
    fun `a journaled network state declares the limit of 100 changes in a session part and no attribute keys`() {
        val journaledArgs = FakeInstrumentationArgs(
            configService = FakeConfigService(
                config = EmbraceConfig(
                    persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                    autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { true }) },
                ),
            ),
        )
        val emitted = mutableListOf<Any>()
        journaledArgs.eventBus.addHandler(StateDeclaration.KEY) { emitted.add(it) }
        journaledArgs.eventBus.addHandler(StateUpdate.KEY) { emitted.add(it) }
        val journaled = NetworkStateDataSource(journaledArgs)
        journaled.onDataCaptureEnabled()
        journaled.onNetworkConnectivityStatusChanged(ConnectivityStatus.Wifi(true))

        // declared once, before the value it starts with, which is its first update
        with(emitted.first() as StateDeclaration) {
            assertEquals("network", state)
            assertEquals(100, maxChangesInPart)
            assertEquals(emptySet<String>(), attributeKeys)
        }
        assertEquals(listOf<Any>(Status.UNKNOWN, Status.WIFI), emitted.drop(1).map { (it as StateUpdate).value })
    }
}
