@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.injection

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.waitUntilConditionMet
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.prefs.defaultPrefsFile
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
internal class ConfigLoadServicesPrefsMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val stallReleased = CountDownLatch(1)

    @Before
    fun setUp() {
        prefs().edit()
            .putString("io.embrace.deviceid", LEGACY_DEVICE_ID)
            .putLong("io.embrace.installtimestamp", 1L)
            .putInt(Ordinal.SESSION.key, 41)
            .commit()
        defaultPrefsFile(context).apply {
            checkNotNull(parentFile).mkdirs()
            writeText("<map />")
        }
    }

    @After
    fun tearDown() {
        stallReleased.countDown()
    }

    @Test
    fun `a config move that times out starts fresh and the next launch doesn't migrate the prefs`() {
        File(context.filesDir, "embrace_remote_config").mkdirs()
        val first = createServices(stallMoveOf = "embrace_remote_config", configMigrationTimeoutMs = 1)
        assertEquals(1, first.ordinalStore.value.incrementAndGet(Ordinal.SESSION))
        assertNotEquals(LEGACY_DEVICE_ID, first.persistedSdkStateStore.value.snapshot().deviceId)
        stallReleased.countDown()

        val second = createServices()
        assertEquals(2, second.ordinalStore.value.incrementAndGet(Ordinal.SESSION))
        assertNotEquals(LEGACY_DEVICE_ID, second.persistedSdkStateStore.value.snapshot().deviceId)
    }

    @Test
    fun `a prefs migration that misses the deadline starts fresh and a late migrator writes nothing`() {
        val services = createServices(stallReadOf = defaultPrefsFile(context).name, prefsMigrationTimeoutMs = 1)
        assertNotEquals(LEGACY_DEVICE_ID, services.persistedSdkStateStore.value.snapshot().deviceId)
        assertEquals("PrefsMigrationFail", errorHandler.sdkErrorMessages.single().msg)
        assertTrue(File(installStateDir(), SdkStorageLayout.ORDINALS_FILE_NAME).exists())
        stallReleased.countDown()

        waitUntilConditionMet(
            waitTimeMs = 5_000,
            dataProvider = { prefs().all.keys.none { it.startsWith("io.embrace.") } },
            condition = { it },
        )
        assertEquals(1, services.ordinalStore.value.incrementAndGet(Ordinal.SESSION))
        val stateFile = File(installStateDir(), SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME)
        assertTrue(!stateFile.exists() || !stateFile.readBytes().decodeToString().contains(LEGACY_DEVICE_ID))
    }

    private fun createServices(
        stallMoveOf: String? = null,
        stallReadOf: String? = null,
        configMigrationTimeoutMs: Long = 3_000L,
        prefsMigrationTimeoutMs: Long = 3_000L,
    ) = ConfigLoadServices(
        context = context,
        preStartServices = PreStartServices(errorHandler = errorHandler),
        configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
        fileSystem = stallingFileSystem(stallMoveOf, stallReadOf),
        configMigrationTimeoutMs = configMigrationTimeoutMs,
        prefsMigrationTimeoutMs = prefsMigrationTimeoutMs,
    )

    private fun stallingFileSystem(moveOf: String?, readOf: String?): FileSystem =
        object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) {
                if (source.name == moveOf) {
                    stallReleased.await()
                }
                super.atomicMove(source, target)
            }

            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path.name == readOf) {
                    stallReleased.await()
                }
                return super.metadataOrNull(path)
            }
        }

    private fun installStateDir() = File(context.noBackupFilesDir, "${SdkStorageLayout.ROOT_DIR_NAME}/install_state")

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private companion object {
        const val LEGACY_DEVICE_ID = "9x-device-id"
    }
}
