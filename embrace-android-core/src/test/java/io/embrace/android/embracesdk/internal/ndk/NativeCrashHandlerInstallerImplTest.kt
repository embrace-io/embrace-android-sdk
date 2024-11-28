package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeMainThreadHandler
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeCrashHandlerInstallerImplTest {

    private val testNativeInstallMessage = NativeInstallMessage(
        markerFilePath = "testMarkerFilePath",
        appState = "testAppState",
        reportId = "testReportId",
        apiLevel = 28,
        is32bit = false,
        devLogging = false,
    )

    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var fakeSharedObjectLoader: FakeSharedObjectLoader
    private lateinit var fakeLogger: FakeEmbLogger
    private lateinit var fakeDelegate: FakeJniDelegate
    private lateinit var fakeMainThreadHandler: FakeMainThreadHandler
    private lateinit var nativeCrashHandlerInstaller: NativeCrashHandlerInstallerImpl
    private lateinit var sessionTracker: FakeSessionIdTracker
    private lateinit var clock: FakeClock
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        fakeConfigService =
            FakeConfigService(autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(sigHandlerDetectionEnabled = true))
        fakeSharedObjectLoader = FakeSharedObjectLoader()
        fakeLogger = FakeEmbLogger(false)
        fakeDelegate = FakeJniDelegate()
        fakeMainThreadHandler = FakeMainThreadHandler()
        sessionTracker = FakeSessionIdTracker()
        clock = FakeClock()
        outputDir = Files.createTempDirectory("test").toFile()

        executorService = BlockingScheduledExecutorService(blockingMode = false)
        nativeCrashHandlerInstaller = NativeCrashHandlerInstallerImpl(
            fakeConfigService,
            fakeSharedObjectLoader,
            fakeLogger,
            fakeDelegate,
            BackgroundWorker(executorService),
            testNativeInstallMessage,
            fakeMainThreadHandler,
            clock,
            sessionTracker,
            { "pid" },
            lazy { outputDir }
        )
    }

    @Test
    fun `install should start native crash monitoring`() {
        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201601000_null_pid_true_native_v1.json", getFilename())
    }

    @Test
    fun `report path containing session ID`() {
        sessionTracker.sessionData = SessionData("sid", true)
        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201601000_sid_pid_true_native_v1.json", getFilename())
    }

    @Test
    fun `report path updated on new session`() {
        nativeCrashHandlerInstaller.install()
        executorService.runCurrentlyBlocked()
        assertEquals("p1_1692201601000_null_pid_true_native_v1.json", getFilename())

        // trigger new session and update report path
        clock.tick(9000)
        sessionTracker.setActiveSession("sid", true)
        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201610000_sid_pid_true_native_v1.json", getFilename())
    }

    @Test
    fun `signal handlers are not reinstalled when the 3rd party signal handler detection is disabled`() {
        fakeConfigService.autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(sigHandlerDetectionEnabled = false)

        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertFalse(fakeDelegate.signalHandlerReinstalled)
    }

    @Test
    fun `signal handlers are not reinstalled when the culprit is in the allow list`() {
        fakeDelegate.culprit = "libwebviewchromium.so"

        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertFalse(fakeDelegate.signalHandlerReinstalled)
    }

    @Test
    fun `signal handlers are not reinstalled when the culprit is null`() {
        fakeDelegate.culprit = null

        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertFalse(fakeDelegate.signalHandlerReinstalled)
    }

    @Test
    fun `signal handlers are reinstalled when the culprit is not in the allow list`() {
        fakeDelegate.culprit = "libtest.so"

        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertTrue(fakeDelegate.signalHandlerReinstalled)
    }

    @Test
    fun `an internal error is tracked when an exception occurs`() {
        fakeSharedObjectLoader.throwWhenLoading = true

        nativeCrashHandlerInstaller.install()

        assertEquals(
            InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL.toString(),
            fakeLogger.internalErrorMessages.last().msg
        )
        assertEquals(SecurityException::class.java, fakeLogger.internalErrorMessages.last().throwable?.javaClass)
    }

    @Test
    fun `do not track internal error when loading embrace native fails`() {
        fakeSharedObjectLoader.failLoad = true

        nativeCrashHandlerInstaller.install()

        assertEquals(0, fakeLogger.internalErrorMessages.size)
        assertFalse(fakeDelegate.signalHandlerInstalled)
    }

    private fun getFilename(): String = File(checkNotNull(fakeDelegate.reportPath)).name
}
