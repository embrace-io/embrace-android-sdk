package io.embrace.android.gradle

import java.io.InputStream

object ResourceReader {
    fun readResource(name: String): InputStream {
        val classLoader = checkNotNull(javaClass.classLoader)
        return classLoader.getResourceAsStream(name)
            ?: error("Could not find resource '$name'")
    }

    fun readResourceAsText(name: String): String {
        return readResource(name).bufferedReader().readText()
    }
}
