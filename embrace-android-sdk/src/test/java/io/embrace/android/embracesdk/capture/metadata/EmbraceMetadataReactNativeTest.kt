package io.embrace.android.embracesdk.capture.metadata

import android.content.Context
import android.content.res.AssetManager
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.system.mockStorageStatsManager
import io.embrace.android.embracesdk.fakes.system.mockWindowManager
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files

internal class EmbraceMetadataReactNativeTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private val fakeClock = FakeClock()
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var buildInfo: BuildInfo
    private lateinit var configService: ConfigService
    private var appFramework: Embrace.AppFramework = Embrace.AppFramework.REACT_NATIVE
    private lateinit var preferencesService: PreferencesService
    private lateinit var processStateService: ProcessStateService
    private lateinit var cpuInfoDelegate: EmbraceCpuInfoDelegate
    private lateinit var mockSharedObjectLoader: SharedObjectLoader
    private val deviceArchitecture = FakeDeviceArchitecture()

    @Before
    fun setUp() {
        context = mockk(relaxed = true) {
            every { packageName } returns "package-name"
            every { getSystemService("window") } returns mockk<WindowManager>(relaxed = true)
        }
        assetManager = mockk(relaxed = true)
        buildInfo = BuildInfo("device-id", null, null)
        configService = FakeConfigService()
        preferencesService = FakePreferenceService()
        processStateService = FakeProcessStateService()
        preferencesService.javaScriptBundleURL = null
        preferencesService.javaScriptPatchNumber = "patch-number"
        preferencesService.reactNativeVersionNumber = "rn-version-number"
        mockSharedObjectLoader = mockk(relaxed = true)
        cpuInfoDelegate = EmbraceCpuInfoDelegate(mockSharedObjectLoader, InternalEmbraceLogger())
        hostedSdkVersionInfo = HostedSdkVersionInfo(preferencesService, InternalEmbraceLogger())
    }

    private fun getMetadataService() = EmbraceMetadataService.ofContext(
        context,
        buildInfo,
        configService,
        appFramework,
        preferencesService,
        processStateService,
        BackgroundWorker(MoreExecutors.newDirectExecutorService()),
        mockStorageStatsManager(),
        mockWindowManager(),
        fakeClock,
        cpuInfoDelegate,
        deviceArchitecture,
        lazy { "" },
        lazy { "" },
        hostedSdkVersionInfo
    )

    @Test
    fun `test React Native bundle ID setting as a default value`() {
        val metadataService = getMetadataService()
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID setting as a default value if jsBundleIdUrl is empty`() {
        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(context, "")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is the same as the value persisted `() {
        preferencesService.javaScriptBundleURL = "javaScriptBundleURL"
        val metadataService = getMetadataService()

        metadataService.setReactNativeBundleId(context, "javaScriptBundleURL")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is a new value`() {
        preferencesService.javaScriptBundleURL = "oldJavaScriptBundleURL"
        val metadataService = getMetadataService()

        metadataService.setReactNativeBundleId(context, "newJavaScriptBundleURL")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        preferencesService.javaScriptBundleURL = null

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(context, "assets://index.android.bundle")

        verify(exactly = 1) { assetManager.open(eq("index.android.bundle")) }

        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in true`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        preferencesService.javaScriptBundleURL = null
        preferencesService.javaScriptBundleId = null

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(context, "assets://index.android.bundle", true)

        verify(exactly = 1) { assetManager.open(eq("index.android.bundle")) }

        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", preferencesService.javaScriptBundleId)
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in false`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        preferencesService.javaScriptBundleURL = "assets://index.android.bundle"
        preferencesService.javaScriptBundleId = "persistedBundleId"

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(context, "assets://index.android.bundle", false)

        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("persistedBundleId", metadataService.getReactNativeBundleId())
        assertEquals("persistedBundleId", preferencesService.javaScriptBundleId)
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param being null`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        preferencesService.javaScriptBundleURL = null
        preferencesService.javaScriptBundleId = null

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(context, "assets://index.android.bundle", null)

        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals(null, preferencesService.javaScriptBundleId)
    }

    @Test
    fun `test React Native bundle ID url as a custom file`() {
        val bundleIdFile = Files.createTempFile("index.android.bundle", "temp").toFile()
        val metadataService = getMetadataService()
        metadataService.setReactNativeBundleId(
            context,
            bundleIdFile.absolutePath
        )
        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test computeReactNativeBundleId with wrong assets path`() {
        preferencesService.javaScriptBundleURL = "assets"
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } throws IOException()

        // computing is null, so reactNativeBundleID should be set to the default value
        val metadataService = getMetadataService()
        assertEquals(metadataService.getReactNativeBundleId(), buildInfo.buildId)
    }

    @Test
    fun `test computeReactNativeBundleId with wrong custom bundle stream`() {
        preferencesService.javaScriptBundleURL = "wrongFilePath"

        // computing is null, so reactNativeBundleID should be set to the default value
        assertEquals(getMetadataService().getReactNativeBundleId(), buildInfo.buildId)
    }
}
