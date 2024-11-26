package io.embrace.android.embracesdk.internal.ndk

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService

internal class EmbraceNdkServiceTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
            mockkStatic(Uuid::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private lateinit var embraceNdkService: EmbraceNdkService
    private lateinit var context: Context
    private lateinit var handler: Handler
    private lateinit var storageManager: FakeStorageService
    private lateinit var configService: FakeConfigService
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var userService: FakeUserService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var sharedObjectLoader: FakeSharedObjectLoader
    private lateinit var logger: EmbLogger
    private lateinit var delegate: JniDelegate
    private lateinit var resources: Resources
    private lateinit var blockableExecutorService: BlockableExecutorService
    private lateinit var sessionIdTracker: FakeSessionIdTracker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        val slot = slot<Runnable>()
        handler = mockk(relaxed = true) {
            every { postAtFrontOfQueue(capture(slot)) } answers {
                slot.captured.run()
                true
            }
        }
        storageManager = FakeStorageService()
        configService = FakeConfigService(autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(ndkEnabled = true))
        processStateService = FakeProcessStateService()
        deliveryService = FakeDeliveryService()
        userService = FakeUserService()
        preferencesService = FakePreferenceService()
        sessionPropertiesService = FakeSessionPropertiesService()
        sharedObjectLoader = FakeSharedObjectLoader()
        logger = EmbLoggerImpl()
        delegate = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        blockableExecutorService = BlockableExecutorService()
        sessionIdTracker = FakeSessionIdTracker()
        sharedObjectLoader.loaded.set(true)
    }

    @After
    fun after() {
        clearAllMocks(staticMocks = false)

        val cacheDir = File("${storageManager.cacheDirectory}/ndk")
        if (cacheDir.exists()) {
            cacheDir.delete()
        }
        val filesDir = File("${storageManager.filesDirectory}/ndk")
        if (filesDir.exists()) {
            filesDir.delete()
        }
    }

    private fun initializeService() {
        embraceNdkService = spyk(
            EmbraceNdkService(
                processStateService,
                userService,
                sessionPropertiesService,
                sharedObjectLoader,
                delegate,
            ),
            recordPrivateCalls = true
        ).apply {
            initializeService(sessionIdTracker = sessionIdTracker)
        }
    }

    @Test
    fun `successful initialization attaches appropriate listeners`() {
        initializeService()
        assertEquals(1, userService.listeners.size)
        assertEquals(1, sessionIdTracker.listeners.size)
        assertEquals(1, sessionPropertiesService.listeners.size)
        assertEquals(1, processStateService.listeners.size)
    }

    @Test
    fun `test updateSessionId where isInstalled true`() {
        initializeService()
        embraceNdkService.updateSessionId("sessionId")
        verify(exactly = 1) { delegate.updateSessionId("sessionId") }
    }

    @Test
    fun `test onBackground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onBackground(0L)
        verify(exactly = 1) { delegate.updateAppState("background") }
    }

    @Test
    fun `test onForeground runs _updateAppState when _updateMetaData was executed and isInstalled true`() {
        initializeService()
        embraceNdkService.onForeground(true, 10)
        verify(exactly = 1) { delegate.updateAppState("foreground") }
    }
}
