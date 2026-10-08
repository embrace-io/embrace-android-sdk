package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class NativeCrashProcessorImplTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var jniDelegate: FakeJniDelegate
    private lateinit var outputDir: File
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var processor: NativeCrashProcessorImpl

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        jniDelegate = FakeJniDelegate()
        outputDir = tempFolder.newFolder("native")
        executor = BlockingScheduledExecutorService(blockingMode = false)
        processor = NativeCrashProcessorImpl(
            args = args,
            sharedObjectLoader = FakeSharedObjectLoader().apply { loadEmbraceNative() },
            delegate = jniDelegate,
            symbolMap = null,
            outputDir = lazy { outputDir },
            worker = PriorityWorker(executor),
        )
    }

    @Test
    fun `native crashes can be deleted one at a time`() {
        val first = storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        val second = storeCrash(crashId = "second", uuid = "bb690ad1-6b87-4e08-b72c-7deca14451d8")

        processor.deleteNativeCrash(processor.getNativeCrashes().single { it.nativeCrashId == "first" })

        assertEquals(listOf("second"), processor.getNativeCrashes().map(NativeCrashData::nativeCrashId))
        assertEquals(setOf(second.name), outputDir.list()?.toSet())
        assertFalse(first.exists())
    }

    @Test
    fun `a deleted native crash is no longer returned even before its associated file is deleted`() {
        val stored = storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        executor.blockingMode = true

        processor.deleteNativeCrash(processor.getNativeCrashes().single())

        // The worker hasn't deleted the file, but the crash is gone from the perspective of NativeCrashProcessor
        assertTrue(stored.exists())
        assertEquals(emptyList<NativeCrashData>(), processor.getNativeCrashes())

        executor.runCurrentlyBlocked()
        assertFalse(stored.exists())
        assertEquals(emptyList<NativeCrashData>(), processor.getNativeCrashes())
    }

    @Test
    fun `native crashes are read from disk only once`() {
        storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        storeCrash(crashId = "second", uuid = "bb690ad1-6b87-4e08-b72c-7deca14451d8")
        val readPaths = mutableListOf<String>()
        val countingProcessor = NativeCrashProcessorImpl(
            args = args,
            sharedObjectLoader = FakeSharedObjectLoader().apply { loadEmbraceNative() },
            delegate = object : JniDelegate by jniDelegate {
                override fun getCrashReport(path: String): String? {
                    readPaths.add(path)
                    return jniDelegate.getCrashReport(path)
                }
            },
            symbolMap = null,
            outputDir = lazy { outputDir },
            worker = PriorityWorker(executor),
        )

        countingProcessor.deleteNativeCrash(nativeCrash("first"))
        repeat(2) {
            assertEquals(listOf("second"), countingProcessor.getNativeCrashes().map(NativeCrashData::nativeCrashId))
        }

        assertEquals(2, readPaths.size)
    }

    @Test
    fun `no native crash is returned once they have all been deleted, even before their files are deleted`() {
        val stored = storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        executor.blockingMode = true

        processor.deleteAllNativeCrashes()

        assertTrue(stored.exists())
        assertEquals(emptyList<NativeCrashData>(), processor.getNativeCrashes())
    }

    @Test
    fun `native crashes are read once the native library is loaded`() {
        storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        val sharedObjectLoader = FakeSharedObjectLoader()
        val lateProcessor = NativeCrashProcessorImpl(
            args = args,
            sharedObjectLoader = sharedObjectLoader,
            delegate = jniDelegate,
            symbolMap = null,
            outputDir = lazy { outputDir },
            worker = PriorityWorker(executor),
        )
        assertEquals(emptyList<NativeCrashData>(), lateProcessor.getNativeCrashes())

        sharedObjectLoader.loadEmbraceNative()

        assertEquals(listOf("first"), lateProcessor.getNativeCrashes().map(NativeCrashData::nativeCrashId))
    }

    @Test
    fun `native crash can be deleted even if the crashes cache hasn't been loaded`() {
        val first = storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")
        val second = storeCrash(crashId = "second", uuid = "bb690ad1-6b87-4e08-b72c-7deca14451d8")
        assertEquals(setOf(first.name, second.name), outputDir.list()?.toSet())

        processor.deleteNativeCrash(nativeCrash("first"))
        assertEquals(setOf(second.name), outputDir.list()?.toSet())
    }

    @Test
    fun `deleting a native crash that is not on disk changes nothing`() {
        val stored = storeCrash(crashId = "first", uuid = "aa690ad1-6b87-4e08-b72c-7deca14451d8")

        processor.deleteNativeCrash(nativeCrash("missing"))

        assertEquals(setOf(stored.name), outputDir.list()?.toSet())
    }

    private fun storeCrash(crashId: String, uuid: String): File {
        val metadata = StoredTelemetryMetadata(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME - 1_000L,
            uuid = uuid,
            processIdentifier = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
            envelopeType = SupportedEnvelopeType.CRASH,
            complete = false,
            payloadType = PayloadType.NATIVE_CRASH,
        )
        val file = File(outputDir, metadata.filename).apply { createNewFile() }
        jniDelegate.addCrashRaw(file.path, args.serializer.toJson(nativeCrash(crashId), NativeCrashData.serializer()))
        return file
    }

    private fun nativeCrash(crashId: String) = NativeCrashData(
        nativeCrashId = crashId,
        sessionPartId = "part-$crashId",
        userSessionId = "user-session",
        timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME - 1_000L,
        crash = "crash-$crashId",
        symbols = null,
    )
}
