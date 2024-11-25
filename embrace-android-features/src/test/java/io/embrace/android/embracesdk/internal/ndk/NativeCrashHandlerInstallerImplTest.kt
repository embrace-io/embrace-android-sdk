package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeMainThreadHandler
import io.embrace.android.embracesdk.fakes.FakeNdkServiceRepository
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeCrashHandlerInstallerImplTest {

    private val testNativeInstallMessage = NativeInstallMessage(
        reportPath = "testReportPath",
        markerFilePath = "testMarkerFilePath",
        sessionId = "testSessionId",
        appState = "testAppState",
        reportId = "testReportId",
        apiLevel = 28,
        is32bit = false,
        devLogging = false,
    )

    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var fakeSharedObjectLoader: FakeSharedObjectLoader
    private lateinit var fakeLogger: FakeEmbLogger
    private lateinit var fakeRepository: FakeNdkServiceRepository
    private lateinit var fakeDelegate: FakeJniDelegate
    private lateinit var fakeMainThreadHandler: FakeMainThreadHandler

    private lateinit var nativeCrashHandlerInstaller: NativeCrashHandlerInstallerImpl

    @Before
    fun setUp() {
        fakeConfigService = FakeConfigService(autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(sigHandlerDetectionEnabled = true))
        fakeSharedObjectLoader = FakeSharedObjectLoader()
        fakeLogger = FakeEmbLogger(false)
        fakeRepository = FakeNdkServiceRepository()
        fakeDelegate = FakeJniDelegate()
        fakeMainThreadHandler = FakeMainThreadHandler()

        nativeCrashHandlerInstaller = NativeCrashHandlerInstallerImpl(
            fakeConfigService,
            fakeSharedObjectLoader,
            fakeLogger,
            fakeRepository,
            fakeDelegate,
            fakeBackgroundWorker(),
            testNativeInstallMessage,
            fakeMainThreadHandler,
        )
    }

    @Test
    fun `install should start native crash monitoring`() {
        nativeCrashHandlerInstaller.install()

        assertTrue(fakeDelegate.signalHandlerInstalled)
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

        assertEquals(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL.toString(), fakeLogger.internalErrorMessages.last().msg)
        assertEquals(SecurityException::class.java, fakeLogger.internalErrorMessages.last().throwable?.javaClass)
    }

    @Test
    fun `do not track internal error when loading embrace native fails`() {
        fakeSharedObjectLoader.failLoad = true

        nativeCrashHandlerInstaller.install()

        assertEquals(0, fakeLogger.internalErrorMessages.size)
        assertFalse(fakeDelegate.signalHandlerInstalled)
    }
}
