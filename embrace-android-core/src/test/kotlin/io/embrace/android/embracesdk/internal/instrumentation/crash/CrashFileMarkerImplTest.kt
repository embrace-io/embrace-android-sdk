package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class CrashFileMarkerImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Lazy reference to the file used by the class being tested
     */
    private lateinit var markerLazyFile: Lazy<File>

    /**
     * Mock of the file used by the class being tested
     */
    private lateinit var mockFile: File

    /**
     * Reference to the real file used in the test
     */
    private lateinit var testFile: File

    /**
     * Class being tested
     */
    private lateinit var crashMarker: CrashFileMarkerImpl

    private lateinit var errorHandler: FakeErrorHandler

    @Before
    fun setUp() {
        testFile = File(tempFolder.root.path, CrashFileMarkerImpl.Companion.CRASH_MARKER_FILE_NAME)
        mockFile = testFile
        markerLazyFile = lazy { mockFile }
        errorHandler = FakeErrorHandler()
        crashMarker = CrashFileMarkerImpl(markerLazyFile, errorHandler)
    }

    @After
    fun tearDown() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun `test calling mark() creates the file`() {
        assertEquals(false, testFile.exists())
        crashMarker.mark()
        assertEquals(true, testFile.exists())
    }

    @Test
    fun `test creating a marker twice rewrites the file without throwing an exception`() {
        assertEquals(false, testFile.exists())
        crashMarker.mark()
        crashMarker.mark()
        assertEquals(true, testFile.exists())
    }

    @Test
    fun `a marker that cannot be written is tracked once`() {
        errorHandler.throwOnSdkError = false
        val unwritable = tempFolder.newFolder("unwritable")
        val marker = CrashFileMarkerImpl(lazy { unwritable }, errorHandler)
        marker.mark()
        assertEquals(
            listOf(SdkErrorType.PayloadStorageFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `test calling removeMark() deletes the file`() {
        crashMarker.mark()
        assertEquals(true, testFile.exists())
        crashMarker.removeMark()
        assertEquals(false, testFile.exists())
    }

    @Test
    fun `test isMarked() returns true if file exists and false if not`() {
        assertEquals(false, testFile.exists())
        assertEquals(false, crashMarker.isMarked())
        crashMarker.mark()
        assertEquals(true, testFile.exists())
        assertEquals(true, crashMarker.isMarked())
    }

    @Test
    fun `test testGetAndCleanMarker() verifies if marker exists and cleans the marker`() {
        assertEquals(false, testFile.exists())
        assertEquals(false, crashMarker.getAndCleanMarker())
        crashMarker.mark()
        assertEquals(true, testFile.exists())
        assertEquals(true, crashMarker.getAndCleanMarker())
        assertEquals(false, crashMarker.getAndCleanMarker())
        assertEquals(false, testFile.exists())
    }
}
