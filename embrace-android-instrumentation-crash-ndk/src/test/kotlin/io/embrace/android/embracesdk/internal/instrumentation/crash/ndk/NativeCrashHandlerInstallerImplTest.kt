package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeMainThreadHandler
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeCrashHandlerInstallerImplTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var fakeSharedObjectLoader: FakeSharedObjectLoader
    private lateinit var fakeDelegate: FakeJniDelegate
    private lateinit var fakeMainThreadHandler: FakeMainThreadHandler
    private lateinit var nativeCrashHandlerInstaller: NativeCrashHandlerInstallerImpl
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var outputDir: File
    private var sessionPartId: String? = null
    private var userSessionId: String? = null

    @Before
    fun setUp() {
        val fakeConfigService =
            FakeConfigService(
                config = EmbraceConfig(autoDataCapture = { AutoDataCaptureConfig(thirdPartySigHandlerDetectionEnabled = { true }) }),
            )
        fakeSharedObjectLoader = FakeSharedObjectLoader()
        fakeDelegate = FakeJniDelegate()
        fakeMainThreadHandler = FakeMainThreadHandler()
        sessionPartId = null
        userSessionId = null
        outputDir = Files.createTempDirectory("test").toFile()
        executorService = BlockingScheduledExecutorService(blockingMode = false)

        args = FakeInstrumentationArgs(
            configService = fakeConfigService,
            errorHandler = FakeErrorHandler(false),
            backgroundWorkerSupplier = { BackgroundWorker(executorService) },
            sessionPartIdSupplier = { sessionPartId },
            userSessionIdSupplier = { userSessionId },
            activeSessionIdsSupplier = {
                SessionIdsSnapshot(
                    userSessionId = userSessionId.orEmpty(),
                    sessionPartId = sessionPartId.orEmpty(),
                )
            },
            processIdentifier = "pid",
        )
        nativeCrashHandlerInstaller = NativeCrashHandlerInstallerImpl(
            args = args,
            sharedObjectLoader = fakeSharedObjectLoader,
            delegate = fakeDelegate,
            mainThreadHandler = fakeMainThreadHandler,
            outputDir = lazy { outputDir },
            reportId = "testReportId",
        )
    }

    @Test
    fun `install should start native crash monitoring`() {
        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201601000_null_pid_true_native_null_null_v2.json", getFilename())
    }

    @Test
    fun `report path containing session part ID and user session ID`() {
        sessionPartId = "sid"
        userSessionId = "usid"
        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201601000_sid_pid_true_native_usid_sid_v2.json", getFilename())
    }

    @Test
    fun `session IDs are forwarded to native on install`() {
        sessionPartId = "sid"
        userSessionId = "usid"
        nativeCrashHandlerInstaller.install()

        assertEquals("sid", fakeDelegate.sessionPartId)
        assertEquals("usid", fakeDelegate.userSessionId)
    }

    @Test
    fun `session IDs are updated on session change`() {
        nativeCrashHandlerInstaller.install()
        executorService.runCurrentlyBlocked()
        assertEquals("null", fakeDelegate.sessionPartId)
        assertEquals("null", fakeDelegate.userSessionId)

        sessionPartId = "sid2"
        userSessionId = "usid2"
        args.sessionChangeListeners.forEach { it.onPostSessionChange() }
        assertEquals("sid2", fakeDelegate.sessionPartId)
        assertEquals("usid2", fakeDelegate.userSessionId)
    }

    @Test
    fun `report path updated on new session`() {
        nativeCrashHandlerInstaller.install()
        executorService.runCurrentlyBlocked()
        assertEquals("p1_1692201601000_null_pid_true_native_null_null_v2.json", getFilename())

        // trigger new session and update report path
        args.clock.tick(9000)
        sessionPartId = "sid"
        userSessionId = "usid"
        args.sessionChangeListeners.forEach { it.onPostSessionChange() }
        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals("p1_1692201610000_sid_pid_true_native_usid_sid_v2.json", getFilename())
    }

    @Test
    fun `signal handlers are not reinstalled when the 3rd party signal handler detection is disabled`() {
        args.configService.config = EmbraceConfig(
            autoDataCapture = { AutoDataCaptureConfig(thirdPartySigHandlerDetectionEnabled = { false }) },
        )

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
    fun `do not track SDK error when loading embrace native fails`() {
        fakeSharedObjectLoader.failLoad = true

        nativeCrashHandlerInstaller.install()

        assertEquals(0, args.errorHandler.sdkErrorMessages.size)
        assertFalse(fakeDelegate.signalHandlerInstalled)
    }

    @Test
    fun `disable after install stops native crash capture`() {
        nativeCrashHandlerInstaller.install()
        assertFalse(fakeDelegate.crashCaptureDisabled)
        nativeCrashHandlerInstaller.disable()
        assertTrue(fakeDelegate.crashCaptureDisabled)
    }

    @Test
    fun `session changes after disable do not update the report path`() {
        nativeCrashHandlerInstaller.install()
        nativeCrashHandlerInstaller.disable()
        val reportPath = fakeDelegate.reportPath

        sessionPartId = "sid2"
        userSessionId = "usid2"
        args.sessionChangeListeners.forEach { it.onPostSessionChange() }
        assertEquals(reportPath, fakeDelegate.reportPath)
        assertEquals("null", fakeDelegate.sessionPartId)
    }

    @Test
    fun `disable before install prevents signal handlers being installed`() {
        nativeCrashHandlerInstaller.disable()
        nativeCrashHandlerInstaller.install()

        assertFalse(fakeDelegate.signalHandlerInstalled)
        assertFalse(fakeDelegate.signalHandlerReinstalled)
        assertNull(fakeDelegate.reportPath)
        assertFalse(fakeDelegate.crashCaptureDisabled)
    }

    @Test
    fun `JNI failure installing signal handlers is tracked and does not stop session tracking`() {
        fakeDelegate.installError = UnsatisfiedLinkError()

        nativeCrashHandlerInstaller.install()

        assertEquals(SdkErrorType.NativeHandlerInstallFail.toString(), args.errorHandler.sdkErrorMessages.single().msg)
        assertFalse(fakeDelegate.signalHandlerInstalled)
        assertFalse(fakeDelegate.signalHandlerReinstalled)
        assertEquals(1, args.sessionChangeListeners.size)
    }

    @Test
    fun `JNI failure checking for overwritten handlers is tracked and does not stop session tracking`() {
        fakeDelegate.checkError = UnsatisfiedLinkError()

        nativeCrashHandlerInstaller.install()

        assertEquals(SdkErrorType.NativeHandlerInstallFail.toString(), args.errorHandler.sdkErrorMessages.single().msg)
        assertTrue(fakeDelegate.signalHandlerInstalled)
        assertEquals(1, args.sessionChangeListeners.size)
    }

    private fun getFilename(): String = File(checkNotNull(fakeDelegate.reportPath)).name
}
