package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.anr.sigquit.FindGoogleThread
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrHandlerNativeDelegate
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.anr.sigquit.SigquitDetectionService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceSigquitDetectionServiceTest {

    private lateinit var configService: FakeConfigService
    private val logger = InternalEmbraceLogger()
    private val mockSharedObjectLoader: SharedObjectLoader = mockk(relaxed = true)
    private val mockFindGoogleThread: FindGoogleThread = mockk(relaxed = true)
    private val mockGoogleAnrHandlerNativeDelegate: GoogleAnrHandlerNativeDelegate = mockk(relaxed = true)
    private val mockGoogleAnrTimestampRepository: GoogleAnrTimestampRepository = mockk(relaxed = true)

    private lateinit var service: SigquitDetectionService

    @Before
    fun setUp() {
        configService = FakeConfigService()
        service = SigquitDetectionService(
            mockSharedObjectLoader,
            mockFindGoogleThread,
            mockGoogleAnrHandlerNativeDelegate,
            mockGoogleAnrTimestampRepository,
            configService,
            logger
        )
    }

    @Test
    fun `finishing initialization won't install anr handler when embrace native library is not loaded`() {
        // given embrace native library is not loaded
        every { mockSharedObjectLoader.loadEmbraceNative() } returns false

        // when finishing initialization
        service.setupGoogleAnrHandler()
        verify(exactly = 0) { mockGoogleAnrHandlerNativeDelegate.install(any()) }
    }

    @Test
    fun `finishing initialization won't install anr handler when google thread was not found`() {
        // given google thread wasn't found
        every { mockSharedObjectLoader.loadEmbraceNative() } returns true
        every { mockFindGoogleThread.invoke() } returns 0

        // when finishing initialization
        service.setupGoogleAnrHandler()
        verify(exactly = 0) { mockGoogleAnrHandlerNativeDelegate.install(any()) }
    }

    @Test
    fun `finishing initialization will install anr handler when google thread was found`() {
        // given google thread wasn't found
        every { mockSharedObjectLoader.loadEmbraceNative() } returns true
        every { mockFindGoogleThread.invoke() } returns 509

        // when finishing initialization
        service.setupGoogleAnrHandler()
        verify(exactly = 1) { mockGoogleAnrHandlerNativeDelegate.install(any()) }
    }

    @Test
    fun `finishing initialization installs anr handler correctly with provided google thread`() {
        // given a google thread
        val testGoogleThreadId = 1234
        every { mockSharedObjectLoader.loadEmbraceNative() } returns true
        every { mockFindGoogleThread.invoke() } returns testGoogleThreadId

        // when finishing initialization
        service.setupGoogleAnrHandler()
        verify(exactly = 1) { mockGoogleAnrHandlerNativeDelegate.install(testGoogleThreadId) }
    }

    @Test
    fun `finishing initialization adds a config service listener when google anr capture is disabled`() {
        // given anr capture is disabled
        assertEquals(0, configService.listeners.size)
        service.initializeGoogleAnrTracking()

        // a listener is added to config service
        assertEquals(1, configService.listeners.size)
    }

    @Test
    fun `clean collections`() {
        // given google thread wasn't found
        every { mockSharedObjectLoader.loadEmbraceNative() } returns true
        every { mockFindGoogleThread.invoke() } returns 509

        // when finishing initialization
        service.cleanCollections()
        verify(exactly = 1) { mockGoogleAnrTimestampRepository.clear() }
    }
}
