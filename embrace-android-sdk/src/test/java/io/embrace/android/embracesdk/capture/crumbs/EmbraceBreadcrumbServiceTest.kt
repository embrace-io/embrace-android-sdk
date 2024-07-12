package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.system.mockActivity
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.internal.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import org.junit.Before

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
        memoryCleanerService = EmbraceMemoryCleanerService(EmbLoggerImpl())
        clock.setCurrentTime(MILLIS_FOR_2020_01_01)
        clock.tickSecond()
    }

    companion object {
        private const val MILLIS_FOR_2020_01_01 = 1577836800000L
    }
}
