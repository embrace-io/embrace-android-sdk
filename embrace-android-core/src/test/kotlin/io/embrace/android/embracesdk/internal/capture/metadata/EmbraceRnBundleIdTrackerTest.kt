package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.Context
import android.content.res.AssetManager
import android.view.WindowManager
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
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
    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        context = mockk(relaxed = true) {
            every { packageName } returns "package-name"
            every { getSystemService("window") } returns mockk<WindowManager>(relaxed = true)
        }
        assetManager = mockk(relaxed = true)
        configService = FakeConfigService().apply {
            appFramework = AppFramework.REACT_NATIVE
        }
        buildInfo = configService.buildInfo
        store = FakeKeyValueStore()
        hostedSdkVersionInfo = ReactNativeSdkVersionInfo(store)
    }

    private fun createRnBundleIdTracker(): RnBundleIdTrackerImpl = RnBundleIdTrackerImpl(
        context,
        configService,
        store,
        fakeBackgroundWorker(),
    )

    @Test
    fun `test React Native bundle ID setting as a default value`() {
        val metadataService = createRnBundleIdTracker()
        assertEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID setting as a default value if jsBundleIdUrl is empty`() {
        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("")
        assertEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is the same as the value persisted `() {
        store.edit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "javaScriptBundleURL")
        }
        val metadataService = createRnBundleIdTracker()

        metadataService.setReactNativeBundleId("javaScriptBundleURL")
        assertEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is a new value`() {
        store.edit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "oldJavaScriptBundleURL")
        }
        val metadataService = createRnBundleIdTracker()

        metadataService.setReactNativeBundleId("newJavaScriptBundleURL")
        assertEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle")

        verify(exactly = 1) { assetManager.open(eq("index.android.bundle")) }

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in true`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", true)

        verify(exactly = 1) { assetManager.open(eq("index.android.bundle")) }

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", store.getString(JAVA_SCRIPT_BUNDLE_ID_KEY))
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in false`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        store.edit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "assets://index.android.bundle")
            putString(JAVA_SCRIPT_BUNDLE_ID_KEY, "persistedBundleId")
        }

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", false)

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("persistedBundleId", metadataService.getReactNativeBundleId())
        assertEquals("persistedBundleId", store.getString(JAVA_SCRIPT_BUNDLE_ID_KEY))
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param being null`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)

        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", null)

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals(null, store.getString(JAVA_SCRIPT_BUNDLE_ID_KEY))
    }

    @Test
    fun `test React Native bundle ID url as a custom file`() {
        val bundleIdFile = Files.createTempFile("index.android.bundle", "temp").toFile()
        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId(bundleIdFile.absolutePath)
        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test computeReactNativeBundleId with wrong assets path`() {
        store.edit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "assets")
        }
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } throws IOException()

        // computing is null, so reactNativeBundleID should be set to the default value
        val metadataService = createRnBundleIdTracker()
        assertEquals(metadataService.getReactNativeBundleId(), buildInfo.rnBundleId)
    }

    @Test
    fun `test computeReactNativeBundleId with wrong custom bundle stream`() {
        store.edit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "wrongFilePath")
        }

        // computing is null, so reactNativeBundleID should be set to the default value
        assertEquals(createRnBundleIdTracker().getReactNativeBundleId(), buildInfo.rnBundleId)
    }

    private companion object {
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
    }
}
