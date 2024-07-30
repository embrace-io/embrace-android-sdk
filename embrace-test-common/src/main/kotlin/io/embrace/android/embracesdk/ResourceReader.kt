package io.embrace.android.embracesdk

import java.io.InputStream

public object ResourceReader {
    public fun readResource(name: String): InputStream {
        val classLoader = checkNotNull(javaClass.classLoader)
        return classLoader.getResourceAsStream(name)
            ?: error("Could not find resource '$name'")
    }

    public fun readResourceAsText(name: String): String {
        return readResource(name).bufferedReader().readText()
    }
}
