package io.embrace.android.gradle.fakes

import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

class FakeConfigFileDirectory(
    private val dirPath: String = "root",
    private val hasConfigFile: Boolean = false
) : Directory {
    var subDirectoriesWithConfigFiles: MutableSet<String> = mutableSetOf()

    override fun getAsFile(): File = File(dirPath)

    override fun getAsFileTree(): FileTree {
        TODO("Not yet implemented")
    }

    override fun dir(path: String): Directory = FakeConfigFileDirectory(
        "$dirPath/$path",
        subDirectoriesWithConfigFiles.contains(path)
    )

    override fun dir(path: Provider<out CharSequence>): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun file(path: String): RegularFile = FakeRegularFile("$dirPath/$path", hasConfigFile)

    override fun file(path: Provider<out CharSequence>): Provider<RegularFile> {
        TODO("Not yet implemented")
    }

    override fun files(vararg paths: Any?): FileCollection {
        TODO("Not yet implemented")
    }
}
