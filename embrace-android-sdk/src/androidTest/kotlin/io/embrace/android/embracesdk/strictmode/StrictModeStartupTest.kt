package io.embrace.android.embracesdk.strictmode

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.config.FakeBaseUrlConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val DRAIN_TIMEOUT_MS = 5000L

private val BACKGROUND_WORKERS = listOf(
    Worker.Background.NonIoRegWorker,
    Worker.Background.IoRegWorker,
    Worker.Background.PeriodicCacheWorker,
    Worker.Background.LogMessageWorker,
    Worker.Background.DeliverySchedulingWorker,
    Worker.Background.HttpRequestWorker,
)

/**
 * Starts the SDK on the main thread with StrictMode enabled, simulating a cold start with no data
 * persisted by a previous run, and fails if the SDK performs any main thread I/O that is not
 * explicitly recorded in [KNOWN_VIOLATIONS].
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RequiresApi(Build.VERSION_CODES.P)
internal class StrictModeStartupTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recorder = StrictModeRecorder()
    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var embrace: EmbraceImpl

    @Before
    fun setUp() {
        simulateColdStart()
    }

    @After
    fun tearDown() {
        if (::embrace.isInitialized) {
            embrace.stop()
        }
    }

    @Test
    fun testSdkStrictMode() {
        instrumentation.runOnMainSync {
            recorder.install()

            bootstrapper = ModuleInitBootstrapper(TestInitModule(InitModuleImpl()))
            EmbraceImpl(bootstrapper).let {
                embrace = it
                it.start(context)
            }
        }

        drainInitWork()
        assertTrue("SDK did not start.", embrace.isStarted)
        assertNoUnexpectedViolations(recorder.violations())
    }

    /**
     * Makes a reasonable attempt to let the instrumentation and background tasks fired by SDK init
     * finish.
     */
    private fun drainInitWork() {
        repeat(2) {
            BACKGROUND_WORKERS.forEach { worker ->
                runCatching {
                    bootstrapper.workerThreadModule.backgroundWorker(worker).submit {}.get(DRAIN_TIMEOUT_MS, MILLISECONDS)
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { }
        }
    }

    /**
     * Deletes everything a previous SDK run could have left behind, so that startup takes the
     * first-launch path.
     */
    private fun simulateColdStart() {
        context.filesDir.deleteContents()
        context.cacheDir.deleteContents()

        // don't warm shared prefs
        File(context.applicationInfo.dataDir, "shared_prefs").deleteRecursively()

        assertFalse(
            File(context.filesDir, "embrace_remote_config").exists(),
        )
    }

    private fun File.deleteContents() {
        listFiles()?.forEach { it.deleteRecursively() }
    }
}

private class TestInitModule(base: InitModule) : InitModule by base {
    override val instrumentedConfig = FakeInstrumentedConfig(
        baseUrls = FakeBaseUrlConfig(
            configImpl = "http://localhost:1",
            dataImpl = "http://localhost:1",
        ),
    )
}
