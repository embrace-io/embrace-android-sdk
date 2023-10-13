package io.embrace.android.embracesdk.internal.crash

import io.mockk.every
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class CrashFileMarkerTest {
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
    private lateinit var crashMarker: CrashFileMarker

    @Before
    fun setUp() {
        testFile = File(tempFolder.root.path, CrashFileMarker.CRASH_MARKER_FILE_NAME)
        mockFile = spyk(testFile)
        markerLazyFile = lazy { mockFile }
        crashMarker = CrashFileMarker(markerLazyFile)
    }

    @After
    fun tearDown() {
        if (testFile.exists()) {
            testFile.delete()
        }
        unmockkAll()
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
    fun `test isMarked() returns false after exception is thrown twice in File_exists()`() {
        crashMarker.mark()
        assertEquals(true, testFile.exists())
        every { mockFile.exists() } throws SecurityException()
        assertEquals(false, crashMarker.isMarked())
    }

    @Test
    fun `test removeMark() tries to delete the file twice when delete() returns false`() {
        crashMarker.mark()
        every { mockFile.delete() } returns false
        crashMarker.removeMark()
        verify(exactly = 2) { mockFile.delete() }
    }

    @Test
    fun `test removeMark() tries to delete the file twice when delete() throws an exception`() {
        crashMarker.mark()
        every { mockFile.delete() } throws SecurityException()
        crashMarker.removeMark()
        verify(exactly = 2) { mockFile.delete() }
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
