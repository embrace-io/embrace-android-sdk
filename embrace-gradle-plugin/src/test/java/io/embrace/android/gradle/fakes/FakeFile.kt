package io.embrace.android.gradle.fakes

import java.io.File

class FakeFile(path: String, var exists: Boolean = true) : File(path) {
    override fun exists(): Boolean = exists
}
