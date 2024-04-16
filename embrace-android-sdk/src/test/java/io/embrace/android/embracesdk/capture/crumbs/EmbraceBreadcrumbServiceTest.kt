package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.fakes.system.mockActivity
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceBreadcrumbServiceTest {

    private lateinit var spanService: FakeSpanService
    private lateinit var configService: ConfigService
    private lateinit var processStateService: ProcessStateService
    private lateinit var memoryCleanerService: EmbraceMemoryCleanerService
    private lateinit var activity: Activity
    private val clock = FakeClock()

    @Before
    fun createMocks() {
        spanService = FakeSpanService()
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        taps = TapsLocalConfig(true),
                        webViewConfig = WebViewLocalConfig(true)
                    )
                },
                remoteCfg = {
                    RemoteConfig()
                }
            )
        )
        processStateService = FakeProcessStateService()
        activity = mockActivity()
        memoryCleanerService = EmbraceMemoryCleanerService(InternalEmbraceLogger())
        clock.setCurrentTime(MILLIS_FOR_2020_01_01)
        clock.tickSecond()
    }

    // TO DO: refactor BreadCrumbService to avoid accessing internal implementation
    @Test
    fun testCleanCollections() {
        val service = initializeBreadcrumbService()
        service.logRnAction("MyAction", 0, 5, mapOf("key" to "value"), 100, "success")

        val breadcrumbs = service.getBreadcrumbs()
        assertEquals(1, breadcrumbs.rnActionBreadcrumbs?.size)

        service.cleanCollections()

        val breadcrumbsAfterClean = service.getBreadcrumbs()
        assertEquals(0, breadcrumbsAfterClean.rnActionBreadcrumbs?.size)
    }

    @Test
    fun testLogRnAction() {
        val service = initializeBreadcrumbService()
        service.logRnAction("MyAction", 0, 5, mapOf("key" to "value"), 100, "success")

        val crumbs = checkNotNull(service.getBreadcrumbs().rnActionBreadcrumbs)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("MyAction", breadcrumb.name)
        assertEquals("success", breadcrumb.output)
        assertEquals(100, breadcrumb.bytesSent)
        assertEquals(mapOf("key" to "value"), breadcrumb.properties)
    }

    private fun initializeBreadcrumbService() = EmbraceBreadcrumbService(
        clock,
        configService,
        { fakeDataSourceModule() },
        InternalEmbraceLogger(),
    )

    companion object {
        private const val MILLIS_FOR_2020_01_01 = 1577836800000L
    }
}
