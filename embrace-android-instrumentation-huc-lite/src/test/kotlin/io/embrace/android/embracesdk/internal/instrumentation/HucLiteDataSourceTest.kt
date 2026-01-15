package io.embrace.android.embracesdk.internal.instrumentation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeURLStreamHandlerFactory
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.instrumentation.huclite.DelegatingInstrumentedURLStreamHandlerFactory
import io.embrace.android.embracesdk.instrumentation.huclite.FAKE_TIME_MS
import io.embrace.android.embracesdk.instrumentation.huclite.InstrumentedUrlStreamHandlerFactory
import io.embrace.android.embracesdk.instrumentation.huclite.testUrl
import io.embrace.android.embracesdk.internal.network.logging.EmbraceDomainCountLimiter
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.net.URL
import java.net.URLStreamHandlerFactory
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class HucLiteDataSourceTest {
    private lateinit var factoryFieldRef: Field
    private lateinit var fakeTelemetryDestination: FakeTelemetryDestination
    private lateinit var fakeTelemetryService: FakeTelemetryService
    private lateinit var fakeClock: FakeClock
    private lateinit var fakeEmbLogger: FakeInternalLogger
    private lateinit var domainCountLimiter: EmbraceDomainCountLimiter
    private lateinit var mockedConnection: HttpsURLConnection
    private lateinit var hucLiteDataSource: HucLiteDataSource
    private var staticFactorySetAttempts = 0

    @Before
    fun setup() {
        factoryField = null
        factoryFieldRef = checkNotNull(this::class.java.declaredFields.find { it.name == "factoryField" })
        factoryFieldRef.isAccessible = true
        staticFactorySetAttempts = 0
        domainCountLimiter = EmbraceDomainCountLimiter(
            defaultLimitSupplier = { 2 },
            domainLimitsSupplier = {
                mapOf(testUrl.host to 4)
            }
        )
        fakeTelemetryDestination = FakeTelemetryDestination()
        fakeTelemetryService = FakeTelemetryService()
        fakeClock = FakeClock(FAKE_TIME_MS)
        fakeEmbLogger = FakeInternalLogger(throwOnInternalError = false)
        mockedConnection =
            mockk<HttpsURLConnection>(relaxed = true).apply {
                every { url } returns testUrl
                every { requestMethod } returns "GET"
                every { responseCode } answers { 200 }
                every { getRequestProperty(any()) } returns null
            }
        hucLiteDataSource = createHucLiteDataSource()
    }

    private fun createHucLiteDataSource(
        networkBehavior: FakeNetworkBehavior = FakeNetworkBehavior(domainCountLimiter = domainCountLimiter),
    ): HucLiteDataSource = HucLiteDataSource(
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            configService = FakeConfigService(networkBehavior = networkBehavior),
            destination = fakeTelemetryDestination,
            logger = fakeEmbLogger,
            clock = fakeClock,
            telemetryService = fakeTelemetryService,
        ),
        streamHandlerFactoryFieldProvider = { factoryFieldRef },
        factoryInstaller = {
            factoryField = it
            attemptToSetURLStreamHandlerFactory(it)
        }
    )

    @Test
    fun `specific domain limit enforced`() {
        repeat(5) {
            hucLiteDataSource.createRequestData(
                wrappedConnection = mockedConnection,
                clock = fakeClock,
            )?.apply {
                startRequest()
                completeRequest(200)
            }
        }
        assertEquals(4, fakeTelemetryDestination.createdSpans.size)
    }

    @Test
    fun `default limit enforced`() {
        every { mockedConnection.url } returns URL("https://fakeurl.burger/test/xyz?doStuff=true")
        repeat(5) {
            hucLiteDataSource.createRequestData(
                wrappedConnection = mockedConnection,
                clock = fakeClock,
            )?.apply {
                startRequest()
                completeRequest(200)
            }
        }
        assertEquals(2, fakeTelemetryDestination.createdSpans.size)
    }

    @Test
    fun `applied limit is tracked when domain limit is exceeded`() {
        // Make 5 requests - the last one should exceed the limit of 4 for testUrl.host
        repeat(5) {
            hucLiteDataSource.createRequestData(
                wrappedConnection = mockedConnection,
                clock = fakeClock,
            )?.apply {
                startRequest()
                completeRequest(200)
            }
        }

        // 4 spans should be recorded, 1 should be dropped
        assertEquals(4, fakeTelemetryDestination.createdSpans.size)
        assertEquals("huc_network_request" to AppliedLimitType.DROP, fakeTelemetryService.appliedLimits.first())
    }

    @Test
    fun `record only if URL is enabled`() {
        val ds = createHucLiteDataSource(
            networkBehavior = FakeNetworkBehavior(
                urlEnabled = false,
                domainCountLimiter = domainCountLimiter,
            )
        )

        ds.createRequestData(
            wrappedConnection = mockedConnection,
            clock = fakeClock,
        )?.apply {
            startRequest()
            completeRequest(200)
        }
        assertEquals(0, fakeTelemetryDestination.createdSpans.size)
    }

    @Test
    fun `initialization with no previous factory works correctly`() {
        hucLiteDataSource.onDataCaptureEnabled()
        assertTrue(factoryField is InstrumentedUrlStreamHandlerFactory)
        assertEquals(1, staticFactorySetAttempts)
        attemptToSetURLStreamHandlerFactory(FakeURLStreamHandlerFactory())
        assertEquals(2, staticFactorySetAttempts)
    }

    @Test
    fun `initialization with existing factory works correctly`() {
        factoryField = FakeURLStreamHandlerFactory()
        hucLiteDataSource.onDataCaptureEnabled()
        assertTrue(factoryField is DelegatingInstrumentedURLStreamHandlerFactory)
    }

    @Test
    fun `initialization via data source only happens once`() {
        hucLiteDataSource.onDataCaptureEnabled()
        hucLiteDataSource.onDataCaptureEnabled()
        assertEquals(1, staticFactorySetAttempts)
    }

    private fun attemptToSetURLStreamHandlerFactory(
        factory: URLStreamHandlerFactory,
    ) {
        try {
            URL.setURLStreamHandlerFactory(factory)
        } catch (t: Throwable) {
            if (t.message != "factory already defined") {
                throw t
            }
        } finally {
            staticFactorySetAttempts++
        }
    }

    companion object {
        var factoryField: URLStreamHandlerFactory? = null
    }
}
