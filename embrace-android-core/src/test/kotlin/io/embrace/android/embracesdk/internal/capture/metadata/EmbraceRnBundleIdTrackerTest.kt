package io.embrace.android.embracesdk.internal.capture.metadata

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

internal class EmbraceRnBundleIdTrackerTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var openedAssets: MutableList<String>
    private var assetStream: InputStream? = null
    private lateinit var buildInfo: BuildInfo
    private lateinit var configService: ConfigService
    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        openedAssets = mutableListOf()
        configService = FakeConfigService().apply {
            appFramework = AppFramework.REACT_NATIVE
        }
        buildInfo = configService.buildInfo
        store = FakeKeyValueStore()
        hostedSdkVersionInfo = ReactNativeSdkVersionInfo(store)
    }

    private fun openAsset(name: String): InputStream {
        openedAssets.add(name)
        return assetStream ?: throw IOException()
    }

    private fun createRnBundleIdTracker(): RnBundleIdTrackerImpl = RnBundleIdTrackerImpl(
        ::openAsset,
        configService,
        store,
        fakeBackgroundWorker(),
    ).apply { startAsyncRetrieval() }

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
        store.editAndCommit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "javaScriptBundleURL")
        }
        val metadataService = createRnBundleIdTracker()

        metadataService.setReactNativeBundleId("javaScriptBundleURL")
        assertEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID from preference if jsBundleIdUrl is a new value`() {
        store.editAndCommit {
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

        assetStream = inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle")

        assertEquals(listOf("index.android.bundle"), openedAssets)

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in true`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)

        assetStream = inputStream

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId("assets://index.android.bundle", true)

        assertEquals(listOf("index.android.bundle"), openedAssets)

        assertNotEquals(buildInfo.rnBundleId, metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", metadataService.getReactNativeBundleId())
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", store.getString(JAVA_SCRIPT_BUNDLE_ID_KEY))
    }

    @Test
    fun `test React Native bundle ID url as Asset with forceUpdate param in false`() {
        val bundleIdFile = Files.createTempFile("bundle-test", ".temp").toFile()
        val inputStream = FileInputStream(bundleIdFile)
        store.editAndCommit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "assets://index.android.bundle")
            putString(JAVA_SCRIPT_BUNDLE_ID_KEY, "persistedBundleId")
        }

        assetStream = inputStream

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

        assetStream = inputStream

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
    fun `test React Native bundle ID for a bundle larger than the read buffer`() {
        // write a bundle bigger than the 8 KB read buffer so the incremental
        // digest loop iterates more than once
        val contents = ByteArray(20_000) { (it % 256).toByte() }
        val bundleIdFile = Files.createTempFile("index.android.bundle", "temp").toFile()
        bundleIdFile.writeBytes(contents)

        val expected = MessageDigest.getInstance("MD5").digest(contents)
            .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
            .uppercase(Locale.US)

        val metadataService = createRnBundleIdTracker()
        metadataService.setReactNativeBundleId(bundleIdFile.absolutePath)

        assertEquals(expected, metadataService.getReactNativeBundleId())
    }

    @Test
    fun `test computeReactNativeBundleId with wrong assets path`() {
        store.editAndCommit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "assets")
        }
        assetStream = null

        // computing is null, so reactNativeBundleID should be set to the default value
        val metadataService = createRnBundleIdTracker()
        assertEquals(metadataService.getReactNativeBundleId(), buildInfo.rnBundleId)
    }

    @Test
    fun `test computeReactNativeBundleId with wrong custom bundle stream`() {
        store.editAndCommit {
            putString(JAVA_SCRIPT_BUNDLE_URL_KEY, "wrongFilePath")
        }

        // computing is null, so reactNativeBundleID should be set to the default value
        assertEquals(createRnBundleIdTracker().getReactNativeBundleId(), buildInfo.rnBundleId)
    }

    private companion object {
        private const val JAVA_SCRIPT_BUNDLE_URL_KEY = "io.embrace.jsbundle.url"
        private const val JAVA_SCRIPT_BUNDLE_ID_KEY = "io.embrace.jsbundle.id"
    }

    @Test
    fun `listener is notified once the bundle ID has been computed in the background`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val tracker = RnBundleIdTrackerImpl(::openAsset, configService, store, BackgroundWorker(executor)).apply {
            startAsyncRetrieval()
        }
        val observed = mutableListOf<String?>()
        tracker.addChangeListener { observed.add(tracker.getReactNativeBundleId()) }
        assertEquals(0, observed.size)
        assertEquals(null, tracker.getReactNativeBundleId())

        // listener should see computed value
        executor.runCurrentlyBlocked()
        assertEquals(listOf(buildInfo.rnBundleId), observed)
    }

    @Test
    fun `listener is notified when the JavaScript bundle URL changes`() {
        val executor = BlockingScheduledExecutorService(blockingMode = true)
        val tracker = RnBundleIdTrackerImpl(::openAsset, configService, store, BackgroundWorker(executor)).apply {
            startAsyncRetrieval()
        }
        executor.runCurrentlyBlocked()

        val observed = mutableListOf<String?>()
        tracker.addChangeListener { observed.add(tracker.getReactNativeBundleId()) }
        tracker.setReactNativeBundleId("index.android.bundle", true)
        executor.runCurrentlyBlocked()

        assertEquals(1, observed.size)
        assertEquals(tracker.getReactNativeBundleId(), observed.single())
    }

    @Test
    fun `setting an unchanged JavaScript bundle URL does not notify listeners`() {
        val tracker = createRnBundleIdTracker()
        tracker.setReactNativeBundleId("index.android.bundle", true)

        var count = 0
        tracker.addChangeListener { count++ }
        tracker.setReactNativeBundleId("index.android.bundle")
        assertEquals(0, count)
    }
}
