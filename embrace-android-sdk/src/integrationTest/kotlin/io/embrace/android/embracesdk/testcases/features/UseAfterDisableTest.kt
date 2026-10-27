package io.embrace.android.embracesdk.testcases.features

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File

/**
 * Work the SDK queued or registered while it was running must not throw, or write data, once it has been disabled:
 * activity callbacks still arrive, init work queued on a worker still runs, and the app can still crash.
 *
 * Startup and UI load tracking take different activity and first-frame callback paths before API 29, so these run with
 * the instrumentation taking both.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UseAfterDisableTest(
    private val otelSdkMode: OtelSdkMode,
    private val platform: Platform,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
            simulatedSdkInt = platform.simulatedSdkInt,
        )
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `activity callbacks after disable are safe while the sdk init span is still queued`() {
        lateinit var errorHandler: FakeErrorHandler
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true),
            ),
            setupAction = {
                errorHandler = getFakeErrorHandler().apply { throwOnSdkError = false }
            },
            testCaseAction = {
                embrace.disable()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            assertAction = {
                assertEquals(emptyList<String>(), errorHandler.sdkErrorMessages.map { it.msg })
            },
        )
    }

    @Test
    fun `activity callbacks after disable are safe once queued init work has run`() {
        lateinit var errorHandler: FakeErrorHandler
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true),
            ),
            setupAction = {
                errorHandler = getFakeErrorHandler().apply { throwOnSdkError = false }
            },
            testCaseAction = {
                embrace.disable()
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false,
                )
            },
            assertAction = {
                assertEquals(emptyList<String>(), errorHandler.sdkErrorMessages.map { it.msg })
            },
        )
    }

    @Test
    fun `nothing is persisted after disable, including by a crash`() {
        lateinit var errorHandler: FakeErrorHandler
        testRule.runTest(
            setupAction = {
                errorHandler = getFakeErrorHandler().apply { throwOnSdkError = false }
            },
            testCaseAction = {
                recordSession {
                    embrace.disable()
                }
                returnIfConditionMet(
                    desiredValueSupplier = { true },
                    dataProvider = { persistedFiles() },
                    condition = { it.isEmpty() },
                    errorMessageSupplier = { "disable() did not clear storage: ${persistedFiles()}" },
                )
                recordSession {
                    embrace.startSpan("span").stop()
                    embrace.logInfo("log")
                    embrace.addBreadcrumb("breadcrumb")
                }
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                simulateJvmUncaughtException(RuntimeException("crash after disable"))
            },
            assertAction = {
                assertEquals(emptyList<File>(), persistedFiles())
                assertEquals(emptyList<String>(), errorHandler.sdkErrorMessages.map { it.msg })
            },
        )
    }

    /**
     * Lists the files left in the SDK's storage. A directory deleted mid-walk counts as not yet settled, so a
     * caller waiting for an empty result tries again.
     */
    private fun persistedFiles(): List<File> = StorageLocation.entries.flatMap { location ->
        val dir = location.asFile(
            errorHandler = FakeErrorHandler(),
            rootDirSupplier = { context.filesDir },
            fallbackDirSupplier = { context.cacheDir },
        ).value
        runCatching { dir.walkTopDown().filter(File::isFile).toList() }.getOrElse { listOf(dir) }
    }

    /**
     * The platform the version-dependent instrumentation behaves as: [simulatedSdkInt] is null for the runtime's own.
     */
    internal enum class Platform(val simulatedSdkInt: Int?) {
        PRE_Q(Build.VERSION_CODES.LOLLIPOP),
        CURRENT(null),
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, {1}")
        fun parameters(): List<Array<Any>> = OtelSdkMode.entries.flatMap { mode ->
            Platform.entries.map { platform -> arrayOf(mode, platform) }
        }
    }
}
