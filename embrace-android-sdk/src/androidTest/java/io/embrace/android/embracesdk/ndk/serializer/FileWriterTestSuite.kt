package io.embrace.android.embracesdk.ndk.serializer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import java.io.File
import java.nio.file.Files
import org.junit.Test

internal class FileWriterTestSuite : NativeTestSuite() {

    external fun run(path: String, expectedJson: String): Int

    @Test
    fun testFileWriter() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val stream = ctx.assets.open("golden-files/native-crash-expected.json")
        val expectedJson = stream.bufferedReader().readText()

        val file = File.createTempFile("test", "txt")
        runNativeTestSuite { run(file.absolutePath, expectedJson) }
    }
}
