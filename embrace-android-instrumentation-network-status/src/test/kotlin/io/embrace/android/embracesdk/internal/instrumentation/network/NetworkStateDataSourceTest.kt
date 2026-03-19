package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkStateDataSourceTest {

    private lateinit var dataSource: NetworkStateDataSource

    @Before
    fun setUp() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
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
                dataSource.getCurrentStateValue()
            )
        }
    }
}
