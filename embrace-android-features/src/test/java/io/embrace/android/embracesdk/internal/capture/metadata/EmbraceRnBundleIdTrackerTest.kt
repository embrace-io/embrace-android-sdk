package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.Context
import android.content.res.AssetManager
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
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

internal class EmbraceRnBundleIdTrackerTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var buildInfo: BuildInfo
    private lateinit var configService: ConfigService
    private lateinit var preferencesService: PreferencesService

    @Before
    fun setUp() {
        context = mockk(relaxed = true) {
            every { packageName } returns "package-name"
            every { getSystemService("window") } returns mockk<WindowManager>(relaxed = true)
        }
        assetManager = mockk(relaxed = true)
        buildInfo = BuildInfo("device-id", null, null)
        configService = FakeConfigService().apply {
            appFramework = AppFramework.REACT_NATIVE
        }
        preferencesService = FakePreferenceService()
        preferencesService.javaScriptBundleURL = null
        preferencesService.javaScriptPatchNumber = "patch-number"
        preferencesService.reactNativeVersionNumber = "rn-version-number"
        hostedSdkVersionInfo = HostedSdkVersionInfo(
            preferencesService,
            AppFramework.REACT_NATIVE
        )
    }

    private fun createRnBundleIdTracker(): RnBundleIdTrackerImpl = RnBundleIdTrackerImpl(
        buildInfo,
        context,
        configService,
        preferencesService,
        BackgroundWorker(MoreExecutors.newDirectExecutorService()),
        FakeEmbLogger()
    )

    @Test
    fun `test React Native bundle ID setting as a default value`() {
        val metadataService = createRnBundleIdTracker()
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID setting as a default value if jsBundleIdUrl is empty`() {
        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is the same as the value persisted `() {
        preferencesService.javaScriptBundleURL = "javaScriptBundleURL"
        val metadataService = createRnBundleIdTracker()

        metadataService.setReactNativeBundleId("javaScriptBundleURL")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is a new value`() {
        preferencesService.javaScriptBundleURL = "oldJavaScriptBundleURL"
        val metadataService = createRnBundleIdTracker()

        metadataService.setReactNativeBundleId("newJavaScriptBundleURL")
        assertEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        preferencesService.javaScriptBundleURL = null

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle")

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

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", true)

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

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", false)

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

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", null)

        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals(null, preferencesService.javaScriptBundleId)
    }

    @Test
    fun `test React Native bundle ID url as a custom file`() {
        val bundleIdFile = Files.createTempFile("index.android.bundle", "temp").toFile()
        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId(bundleIdFile.absolutePath)
        assertNotEquals(buildInfo.buildId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test computeReactNativeBundleId with wrong assets path`() {
        preferencesService.javaScriptBundleURL = "assets"
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } throws IOException()

        // computing is null, so reactNativeBundleID should be set to the default value
        val metadataService = createRnBundleIdTracker()
        assertEquals(metadataService.getReactNativeBundleId(), buildInfo.buildId)
    }

    @Test
    fun `test computeReactNativeBundleId with wrong custom bundle stream`() {
        preferencesService.javaScriptBundleURL = "wrongFilePath"

        // computing is null, so reactNativeBundleID should be set to the default value
        assertEquals(createRnBundleIdTracker().getReactNativeBundleId(), buildInfo.buildId)
    }
}
