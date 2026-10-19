package io.embrace.android.embracesdk.macrobenchmark.app.fixture

import io.embrace.android.embracesdk.macrobenchmark.app.storage.FixtureStorage
import io.embrace.android.embracesdk.macrobenchmark.app.storage.SdkStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class FixtureRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dataDir: File
    private lateinit var sdk: SdkStorage
    private lateinit var recorder: FixtureRecorder
    private var sdkStarted = true

    @Before
    fun setUp() {
        dataDir = tmp.newFolder("data")
        sdk = SdkStorage(dataDir)
        sdkStarted = true
        recorder = FixtureRecorder(sdk, FixtureStorage(dataDir), { sdkStarted }, writeSettleMs = 0)
    }

    @Test
    fun `recording runs the app instance and saves every session part it leaves`() {
        var ran = false
        val outcome = recorder.record {
            ran = true
            writePart("1000_a")
            writePart("1500_b")
        }
        assertEquals(FixtureOutcome.Recorded, outcome)
        assertTrue(ran)

        sdkStarted = false
        recorder.restore()
        assertEquals(listOf("1000_a", "1500_b"), sdk.sessionPartDirs())
    }

    @Test
    fun `each recorded app instance adds its parts to the fixture`() {
        recorder.record { writePart("1000_a") }
        // a recording launch starts from cleared storage, which leaves the fixture alone
        sdk.clear()
        assertEquals(FixtureOutcome.Recorded, recorder.record { writePart("2000_b") })

        sdkStarted = false
        recorder.restore()
        assertEquals(listOf("1000_a", "2000_b"), sdk.sessionPartDirs())
    }

    @Test
    fun `an app instance can't be recorded without the sdk running`() {
        sdkStarted = false
        var ran = false
        assertEquals(FixtureOutcome.SdkNotStarted, recorder.record { ran = true })
        assertFalse(ran)
    }

    @Test
    fun `restoring clears what the sdk wrote and puts the fixture's parts back`() {
        recorder.record { writePart("1000_a") }
        sdkStarted = false
        // what the previous iteration's launch left
        writePart("3000_c")
        sdk.writeRemoteConfig("{}")

        assertEquals(FixtureOutcome.Restored, recorder.restore())
        assertEquals(listOf("1000_a"), sdk.sessionPartDirs())
        assertFalse(sdk.remoteConfigDir.exists())
    }

    @Test
    fun `restoring is repeatable, as every iteration restores the same fixture`() {
        recorder.record { writePart("1000_a") }
        sdkStarted = false
        repeat(3) {
            assertEquals(FixtureOutcome.Restored, recorder.restore())
            assertEquals(listOf("1000_a"), sdk.sessionPartDirs())
        }
    }

    @Test
    fun `restoring refuses while the sdk is running`() {
        recorder.record { writePart("1000_a") }
        writePart("3000_c")

        assertEquals(FixtureOutcome.SdkRunning, recorder.restore())
        assertEquals(listOf("1000_a", "3000_c"), sdk.sessionPartDirs())
    }

    @Test
    fun `restoring with nothing recorded is reported`() {
        sdkStarted = false
        assertEquals(FixtureOutcome.NothingToRestore, recorder.restore())
    }

    private fun writePart(name: String) {
        File(sdk.sessionsDir, name).apply { mkdirs() }.resolve("metadata.pb").writeText("metadata")
    }
}
