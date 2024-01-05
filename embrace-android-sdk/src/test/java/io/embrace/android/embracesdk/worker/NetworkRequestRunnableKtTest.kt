package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.comms.api.ApiRequestMapper
import io.embrace.android.embracesdk.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NetworkRequestRunnableKtTest {

    private val builder = EmbraceApiUrlBuilder(
        coreBaseUrl = "https://data.emb-api.com",
        configBaseUrl = "https://config.emb-api.com",
        appId = "appId",
        lazyDeviceId = lazy { "deviceId" },
        lazyAppVersionName = lazy { "appVersionName" }
    )
    private val mapper = ApiRequestMapper(
        builder,
        lazy { "deviceId" },
        "appVersionName",
    )

    @Test
    fun `queue prioritises important requests`() {
        val queue = createNetworkRequestQueue()
        val log1 = NetworkRequestRunnable(
            mapper.logRequest(
                EventMessage(
                    Event(
                        eventId = "1",
                        type = EmbraceEvent.Type.INFO_LOG
                    )
                )
            )
        ) {}
        val crash1 = NetworkRequestRunnable(
            mapper.logRequest(
                EventMessage(
                    Event(
                        eventId = "2",
                        type = EmbraceEvent.Type.CRASH
                    )
                )
            )
        ) {}
        val log2 = NetworkRequestRunnable(
            mapper.logRequest(
                EventMessage(
                    Event(
                        eventId = "3",
                        type = EmbraceEvent.Type.INFO_LOG
                    )
                )
            )
        ) { }
        val session1 = NetworkRequestRunnable(mapper.sessionRequest()) { }
        val session2 = NetworkRequestRunnable(mapper.sessionRequest()) { }
        val session3 = NetworkRequestRunnable(mapper.sessionRequest()) { }

        val jobs = listOf(log1, crash1, session3, session1, log2, session2)
        jobs.forEach(queue::add)

        // sessions are always first. otherwise order is indeterminate.
        val expected = listOf(session1, session2, session3, log1, crash1, log2).map { it.ordinal }

        val list = mutableListOf<NetworkRequestRunnable>()
        while (queue.isNotEmpty()) {
            list.add(checkNotNull(queue.poll()))
        }

        val observed = list.map { it.ordinal }
        assertEquals(expected, observed)
    }
}
