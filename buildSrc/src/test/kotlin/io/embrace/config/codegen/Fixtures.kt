package io.embrace.config.codegen

import org.junit.Assert.assertEquals
import java.io.File

internal object Fixtures {

    fun read(path: String): String = checkNotNull(javaClass.getResource("/$path")) { "missing fixture '$path'" }.readText()

    fun parse(path: String): List<ConfigSlice> = ConfigSlice.fromYaml(read(path))

    fun invalidConfigs(): List<String> = read("invalid.yaml").split("\n---\n")

    fun assertMatches(name: String, actual: String) {
        val path = "expected/$name.txt"
        if (System.getenv("UPDATE_FIXTURES") != null) {
            File("src/test/resources/$path").writeText(actual)
        } else {
            assertEquals(path, read(path), actual)
        }
    }
}
