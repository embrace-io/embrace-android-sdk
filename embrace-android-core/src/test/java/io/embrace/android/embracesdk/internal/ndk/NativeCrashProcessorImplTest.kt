package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeSymbolService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class NativeCrashProcessorImplTest {

    private lateinit var service: NativeCrashProcessor
    private lateinit var configService: FakeConfigService
    private lateinit var sharedObjectLoader: FakeSharedObjectLoader
    private lateinit var logger: EmbLogger
    private lateinit var delegate: FakeJniDelegate
    private lateinit var blockableExecutorService: BlockableExecutorService
    private lateinit var storageDir: File
    private lateinit var worker: PriorityWorker<StoredTelemetryMetadata>
    private val serializer = EmbraceSerializer()

    @Before
    fun setup() {
        storageDir = Files.createTempDirectory("ndk").toFile().apply {
            mkdirs()
        }
        configService = FakeConfigService(autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(ndkEnabled = true))
        sharedObjectLoader = FakeSharedObjectLoader().apply { loadEmbraceNative() }
        logger = EmbLoggerImpl()
        delegate = FakeJniDelegate()
        blockableExecutorService = BlockableExecutorService()
        worker = PriorityWorker(blockableExecutorService)
    }

    private fun initializeService() {
        service = NativeCrashProcessorImpl(
            sharedObjectLoader,
            logger,
            delegate,
            serializer,
            FakeSymbolService(mapOf("symbol1" to "test")),
            lazy { storageDir },
            worker
        )
    }

    @Test
    fun `test getLatestNativeCrash does nothing if there are no matchingFiles`() {
        initializeService()
        val result = service.getLatestNativeCrash()
        assertNull(result)
    }

    @Test
    fun `test getLatestNativeCrash catches an exception if _getCrashReport returns an empty string`() {
        initializeService()
        addCrashFiles("test")
        val crashData = service.getLatestNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test getLatestNativeCrash catches an exception if _getCrashReport returns invalid json syntax`() {
        val json = "{\n" +
            "  \"sid\": [\n" +
            "    {\n" +
            "    }\n" +
            "  ]\n" +
            "}"
        delegate.crashRaw = json

        initializeService()
        addCrashFiles("test")
        val crashData = service.getLatestNativeCrash()
        assertNull(crashData)
    }

    @Test
    fun `test getLatestNativeCrash when a native crash was captured`() {
        delegate.crashRaw = getNativeCrashRaw()
        configService.appFramework = AppFramework.UNITY

        initializeService()
        addCrashFiles("test")

        val result = checkNotNull(service.getLatestNativeCrash())
        with(result) {
            assertNotNull(crash)
        }
    }

    @Test
    fun `test getLatestNativeCrash when there is no native crash does not execute crash files logic`() {
        configService.appFramework = AppFramework.UNITY
        initializeService()

        val result = service.getLatestNativeCrash()
        assertNull(result)
    }

    @Test
    fun `getNativeCrashes returns all the crashes in the repository and doesn't invoke delete`() {
        delegate.crashRaw = getNativeCrashRaw()
        initializeService()
        addCrashFiles("file1")
        addCrashFiles("file2")
        assertEquals(2, service.getNativeCrashes().size)
        assertEquals(2, service.getNativeCrashes().size)
    }

    @Test
    fun `getLatestNativeCrash returns only one crash even if there are many and deletes them all`() {
        delegate.crashRaw = getNativeCrashRaw()
        initializeService()
        addCrashFiles("file1")
        addCrashFiles("file2")
        assertNotNull(service.getLatestNativeCrash())
        assertEquals(0, service.getNativeCrashes().size)
    }

    private fun addCrashFiles(name: String) {
        val metadata = StoredTelemetryMetadata(
            timestamp = 1000000,
            uuid = name,
            processId = "pid",
            envelopeType = SupportedEnvelopeType.CRASH,
            payloadType = PayloadType.NATIVE_CRASH,
        )
        File(storageDir, metadata.filename).createNewFile()
    }

    private fun getNativeCrashRaw() = ResourceReader.readResourceAsText("native_crash_raw.txt")
}
