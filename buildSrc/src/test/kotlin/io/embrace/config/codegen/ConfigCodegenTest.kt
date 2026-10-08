package io.embrace.config.codegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodegenTest {

    @Test
    fun `generates each config and its test`() {
        Fixtures.parse("foo.yaml").forEach {
            Fixtures.assertMatches("${it.name}.kt", configSliceFile(it, "com.example", "foo.yaml").toString())
            Fixtures.assertMatches("${it.name}GeneratedTest.kt", configTestFile(it, "com.example", "foo.yaml").toString())
            localConfigFiles(it, "foo.yaml").forEach { file -> Fixtures.assertMatches("${file.name}.kt", file.toString()) }
        }
    }

    @Test
    fun `generated tests only support top-level remote properties`() {
        val slice = Fixtures.parse("nested-remote.yaml").single()
        assertTrue("remote?.fooConfig?.limit" in configSliceFile(slice, "com.example", "nested-remote.yaml").toString())
        assertEquals(
            "FooConfig.limit: generated tests only support top-level RemoteConfig properties",
            assertThrows(IllegalArgumentException::class.java) { configTestFile(slice, "com.example", "nested-remote.yaml") }.message,
        )
    }

    @Test
    fun `remote-only configs have no local config`() {
        assertEquals(emptyList<Any>(), localConfigFiles(Fixtures.parse("nested-remote.yaml").single(), "nested-remote.yaml"))
    }

    @Test
    fun `rejects invalid configs`() {
        Fixtures.invalidConfigs().forEach { yaml ->
            val expected = yaml.lineSequence().first().removePrefix("# error: ")
            assertEquals(expected, assertThrows(expected, IllegalArgumentException::class.java) { ConfigSlice.fromYaml(yaml) }.message)
        }
    }
}
