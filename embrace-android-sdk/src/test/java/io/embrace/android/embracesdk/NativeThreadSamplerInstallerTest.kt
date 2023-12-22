package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class NativeThreadSamplerInstallerTest {

    private lateinit var sampler: EmbraceNativeThreadSamplerService
    private lateinit var configService: ConfigService
    private lateinit var anrService: AnrService
    private lateinit var delegate: EmbraceNativeThreadSamplerService.NdkDelegate
    private lateinit var cfg: AnrRemoteConfig

    @Before
    fun setUp() {
        anrService = mockk(relaxed = true)
        delegate = mockk(relaxed = true)
        sampler = mockk(relaxed = true)

        cfg = AnrRemoteConfig(pctNativeThreadAnrSamplingEnabled = 100f)
        configService = FakeConfigService(anrBehavior = fakeAnrBehavior { cfg })
    }

    @Test
    fun testInstallDisabled() {
        val installer = NativeThreadSamplerInstaller()
        installer.monitorCurrentThread(sampler, configService, anrService)
        verify(exactly = 0) { delegate.setupNativeThreadSampler(false) }
    }

    @Test
    fun testInstallEnabledSuccess() {
        val installer = NativeThreadSamplerInstaller()
        every { sampler.setupNativeSampler() } returns true
        every { sampler.monitorCurrentThread() } returns true

        repeat(5) {
            installer.monitorCurrentThread(sampler, configService, anrService)
        }
        verify(exactly = 1) { sampler.monitorCurrentThread() }
        verify(exactly = 1) { anrService.addBlockedThreadListener(sampler) }
    }

    @Test
    fun testInstallEnabledFailure() {
        val installer = NativeThreadSamplerInstaller()
        every { sampler.setupNativeSampler() } returns false
        every { sampler.monitorCurrentThread() } returns false
        sampler.setupNativeSampler()

        repeat(5) {
            installer.monitorCurrentThread(sampler, configService, anrService)
        }
        verify(exactly = 5) { sampler.monitorCurrentThread() }
        verify(exactly = 0) { anrService.addBlockedThreadListener(sampler) }

        // now do a successful install
        every { sampler.setupNativeSampler() } returns true
        every { sampler.monitorCurrentThread() } returns true
        sampler.setupNativeSampler()

        repeat(5) {
            installer.monitorCurrentThread(sampler, configService, anrService)
        }
        verify(exactly = 6) { sampler.monitorCurrentThread() }
        verify(exactly = 1) { anrService.addBlockedThreadListener(sampler) }
    }

    @Test
    fun testConfigListener() {
        val installer = NativeThreadSamplerInstaller()
        every { sampler.setupNativeSampler() } returns true
        every { sampler.monitorCurrentThread() } returns true
        sampler.setupNativeSampler()

        repeat(5) {
            installer.monitorCurrentThread(sampler, configService, anrService)
        }
        verify(exactly = 1) { sampler.monitorCurrentThread() }
        verify(exactly = 1) { anrService.addBlockedThreadListener(sampler) }
    }

    @Test
    fun testInstallNewThread() {
        val installer = NativeThreadSamplerInstaller()
        every { sampler.setupNativeSampler() } returns true
        every { sampler.monitorCurrentThread() } returns true

        installer.monitorCurrentThread(sampler, configService, anrService)
        verify(exactly = 1) { sampler.monitorCurrentThread() }
        verify(exactly = 1) { anrService.addBlockedThreadListener(sampler) }
        assertEquals(installer.currentThread, Thread.currentThread())

        val executor = Executors.newSingleThreadExecutor()
        var executorThread: Thread? = null
        executor.submit {
            executorThread = Thread.currentThread()
            installer.monitorCurrentThread(sampler, configService, anrService)
        }.get(1, TimeUnit.SECONDS)
        assertEquals(installer.currentThread, executorThread)
    }
}
