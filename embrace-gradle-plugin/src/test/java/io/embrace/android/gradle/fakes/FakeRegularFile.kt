package io.embrace.android.gradle.fakes

import org.gradle.api.file.RegularFile
import java.io.File

class FakeRegularFile(private val path: String, private val exists: Boolean) : RegularFile {
    override fun getAsFile(): File = FakeFile(path, exists)
}
