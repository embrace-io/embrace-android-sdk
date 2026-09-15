package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER_NOT_FOUND
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.STRING_ERROR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

internal class ArtOptimizationStateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val beginToken = "oat\n228\u0000"
    private val supportedAbi = "arm64-v8a"
    private val supportedIsa = "arm64"
    private lateinit var apk: File
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        val apkDir = tmp.newFolder("data", "app", "~~abc", "io.embrace.test-xyz")
        apk = File(apkDir, "base.apk").apply { writeBytes(ByteArray(16)) }
        logger = FakeInternalLogger(throwOnInternalError = false)
    }

    @Test
    fun `odex read from standard location and differentiates between file not found and location not found`() {
        val noOdex = createState()
        assertNotNull(noOdex)
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, noOdex.artCompilerFilter)
        assertFalse(noOdex.hasAppImage)

        writeOdex(createOatHeader("verify", 1024), "arm64")
        writeArt("arm")
        val arm64State = createState()
        assertEquals("verify", arm64State.artCompilerFilter)
        assertFalse(arm64State.hasAppImage)

        writeOdex(createOatHeader("speed-profile", 1024), "arm")
        val armState = createState(abi = "armeabi-v7a")
        assertEquals("speed-profile", armState.artCompilerFilter)
        assertTrue(armState.hasAppImage)

        assertNullState(abi = "unknown")
        // an APK path with no directory that could hold an oat one alongside it
        assertNullState(apkPath = "base.apk")
    }

    @Test
    fun `compiler filter is read from the oat blob only if it is fully in the first 64 KB`() {
        writeArt(supportedIsa)
        writeOdex(createOatHeader("speed-profile", 0))
        assertEquals("speed-profile", createState().artCompilerFilter)
        writeOdex(createOatHeader("everything", 40_000))
        assertEquals("everything", createState().artCompilerFilter)

        // compiler filter beyond part of header that is read - filter unknown but an app image was found
        writeOdex(createOatHeader("speed", 70_000))
        val filterNotFoundState = createState()
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, filterNotFoundState.artCompilerFilter)
        assertTrue(filterNotFoundState.hasAppImage)

        // compiler filter value cut off by the end of the part that is read - no filter rather than a truncated one
        val filterValueOffset = String(createOatHeader("speed-profile", 0), Charsets.US_ASCII).indexOf("speed-profile")
        writeOdex(createOatHeader("speed-profile", 65_536 - filterValueOffset - 3))
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, createState().artCompilerFilter)
    }

    @Test
    fun `an odex without a readable compiler filter reports no filter`() {
        writeOdex("no oat header here".toByteArray())
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, createState().artCompilerFilter)

        writeOdex(ByteArray(0))
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, createState().artCompilerFilter)

        writeOdex("compiler-filter\u0000\u0000debuggable\u0000false\u0000".toByteArray())
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, createState().artCompilerFilter)

        // the key must be followed by NUL character
        writeOdex("compiler-filter-extra\u0000verify\u0000".toByteArray())
        assertEquals(ART_COMPILER_FILTER_NOT_FOUND, createState().artCompilerFilter)

        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `an odex we can see but not read has error value for the filter but the correct app image value`() {
        writeOdex(createOatHeader("speed-profile", 16))
        writeArt()

        // Create an unreadable odex in the expected location
        File(oatDir(supportedIsa), "base.odex").apply {
            setReadable(false)
            assertFalse(canRead())
        }

        val state = createState()
        assertEquals(STRING_ERROR, state.artCompilerFilter)
        assertTrue(state.hasAppImage)

        val reported = logger.internalErrorMessages.single()
        assertEquals("SdkInitAttributeCaptureFail", reported.msg)
        assertTrue(reported.throwable?.message?.endsWith(ART_COMPILER_FILTER) == true)
        assertTrue(reported.throwable?.cause is IOException)
    }

    @Test
    fun `location of odex determined by primary ABI`() {
        writeOdex(createOatHeader("speed-profile", 16), "arm64")
        writeOdex(createOatHeader("verify", 16), "arm")
        writeOdex(createOatHeader("speed", 16), "x86_64")
        writeOdex(createOatHeader("everything", 16), "x86")

        assertEquals("speed-profile", createState(abi = "arm64-v8a").artCompilerFilter)
        assertEquals("verify", createState(abi = "armeabi-v7a").artCompilerFilter)
        assertEquals("speed", createState(abi = "x86_64").artCompilerFilter)
        assertEquals("everything", createState(abi = "x86").artCompilerFilter)

        assertNullState(abi = "riscv64")
        assertNullState(abi = "unknown")
        assertNullState(abi = "")
    }

    private fun createState(
        apkPath: String = apk.path,
        abi: String = supportedAbi,
    ): ArtOptimizationState = checkNotNull(ArtOptimizationState.create(apkPath = apkPath, primaryAbi = abi, logger = logger))

    private fun assertNullState(apkPath: String = apk.path, abi: String = supportedAbi) {
        assertNull(ArtOptimizationState.create(apkPath = apkPath, primaryAbi = abi, logger = logger))
    }

    private fun writeOdex(bytes: ByteArray, isa: String = supportedIsa) {
        File(oatDir(isa), "base.odex").writeBytes(bytes)
    }

    private fun writeArt(isa: String = supportedIsa) {
        File(oatDir(isa), "base.art").writeBytes(ByteArray(8))
    }

    private fun oatDir(isa: String): File = File(apk.parentFile, "oat/$isa").apply { mkdirs() }

    /**
     * An oat-header-shaped blob with [padding] bytes of junk data that precedes the expected format of a blob in which
     * we can find the ART compiler filter.
     */
    private fun createOatHeader(filter: String, padding: Int): ByteArray {
        val blob = "dex2oat-cmdline\u0000--x\u0000debuggable\u0000false\u0000" +
            "compiler-filter\u0000$filter\u0000has-patch-info\u0000false\u0000"
        return ByteArray(padding) { 0x7f } + beginToken.toByteArray() + blob.toByteArray(Charsets.US_ASCII)
    }
}
