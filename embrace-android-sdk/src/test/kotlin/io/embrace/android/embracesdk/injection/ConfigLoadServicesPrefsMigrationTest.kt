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
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
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
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class ConfigLoadServicesPrefsMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val stallReached = CountDownLatch(1)
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
    fun `the prefs migrate when a 9x config is present`() {
        File(context.filesDir, "embrace_remote_config").mkdirs()
        val services = createServices()
        assertEquals(42, services.ordinalStore.value.incrementAndGet(Ordinal.SESSION))
        assertEquals(LEGACY_DEVICE_ID, services.persistedSdkStateStore.value.snapshot().deviceId)
    }

    @Test
    fun `a stalled prefs migration doesn't delay the telemetry move, starts fresh and a late migrator writes nothing`() {
        File(context.filesDir, "embrace").mkdirs()
        val services = createServices(stallReadOf = defaultPrefsFile(context).name, migrationBudgetMs = 1_000)
        services.legacyTelemetryMigration.move(services.migrationBudget)
        assertTrue(services.legacyTelemetryMigration.isComplete)

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

    @Test
    fun `stores adopt the decoded prefs while the migration is still writing them`() {
        val services = createServices(stallMoveTo = SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME)
        assertTrue(stallReached.await(5, TimeUnit.SECONDS))
        assertEquals(LEGACY_DEVICE_ID, services.persistedSdkStateStore.value.snapshot().deviceId)
        assertEquals(42, services.ordinalStore.value.incrementAndGet(Ordinal.SESSION))
        stallReleased.countDown()

        val ordinals = File(installStateDir(), SdkStorageLayout.ORDINALS_FILE_NAME)
        waitUntilConditionMet(waitTimeMs = 5_000, dataProvider = { ordinals.exists() }, condition = { it })
        assertEquals(43, checkNotNull(MmapOrdinalStore.open(ordinals, errorHandler)).incrementAndGet(Ordinal.SESSION))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    private fun createServices(
        stallReadOf: String? = null,
        stallMoveTo: String? = null,
        migrationBudgetMs: Long = 3_000L,
    ) = ConfigLoadServices(
        context = context,
        preStartServices = PreStartServices(errorHandler = errorHandler),
        configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
        fileSystem = stallingFileSystem(stallReadOf, stallMoveTo),
        migrationBudgetMs = migrationBudgetMs,
    )

    private fun stallingFileSystem(readOf: String?, moveTo: String?): FileSystem =
        object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path.name == readOf) {
                    stallReleased.await()
                }
                return super.metadataOrNull(path)
            }

            override fun atomicMove(source: Path, target: Path) {
                if (target.name == moveTo) {
                    stallReached.countDown()
                    stallReleased.await()
                }
                super.atomicMove(source, target)
            }
        }

    private fun installStateDir() = File(context.noBackupFilesDir, "${SdkStorageLayout.ROOT_DIR_NAME}/install_state")

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private companion object {
        const val LEGACY_DEVICE_ID = "9x-device-id"
    }
}
