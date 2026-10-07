package io.embrace.config.codegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodegenTest {

    private val yaml = checkNotNull(javaClass.getResource("/foo.yaml")).readText()

    @Test
    fun `generates each config`() {
        val slices = parse(yaml)
        assertEquals(listOf("FooConfig", "BarConfig"), slices.map { it.name })
        val foo = configSliceFile(slices[0], "com.example", "foo.yaml").toString()
        assertTrue(foo, "crossinline limit: () -> Int = { 10 }" in foo)
        assertTrue(foo, "override val limit: Int = limit()" in foo)
        assertTrue(foo, "name = { remote?.fooName ?: \"a \\\"b\\\" \${'$'}c\" }" in foo)
        val bar = configSliceFile(slices[1], "com.example", "foo.yaml").toString()
        assertTrue(bar, "Resolved bar config." in bar)
        assertTrue(bar, "fun resolveBar(local: InstrumentedConfig): BarConfig = BarConfig(" in bar)
    }

    @Test
    fun `generates each config test`() {
        val bar = configTestFile(parse(yaml)[1], "com.example", "foo.yaml").toString()
        assertTrue(bar, "internal class BarConfigGeneratedTest" in bar)
        assertTrue(bar, "fun `limit local used when remote absent`()" in bar)
        assertTrue(bar, "assertEquals(1, resolveBar(local = overrideLocal(\"foo.getLimit()\", 1)).limit)" in bar)
        val foo = configTestFile(parse(yaml.replace("sdk: fooConfig?.limit", "sdk: fooLimit"))[0], "com.example", "foo.yaml").toString()
        assertTrue(foo, "RemoteConfig(pctFooEnabled = 0f), bucket = unreadBucket).enabled)" in foo)
        assertTrue(foo, "bucket = lazy { 49f }).enabled)" in foo)
        assertTrue(foo, "assertEquals(\"b\", resolveFoo(local = InstrumentedConfigImpl, remote = RemoteConfig(fooName = \"b\")" in foo)
        assertEquals(
            "FooConfig.limit: generated tests only support top-level RemoteConfig properties",
            assertThrows(IllegalArgumentException::class.java) { configTestFile(parse(yaml)[0], "com.example", "foo.yaml") }.message,
        )
    }

    @Test
    fun `rejects invalid configs`() {
        mapOf(
            yaml + "\nextra: 1" to "extra: expected a map",
            yaml.replace("      sdk: enabledFeatures.isFooEnabled()\n", "") to "FooConfig.foo_enabled: local: missing 'sdk'",
            yaml.replace("type: Boolean", "type: Bool") to "FooConfig.foo_enabled: local: unknown type 'Bool'",
            yaml.replace("type: Boolean", "type: Pct") to "FooConfig.foo_enabled: Pct is only valid for remote",
            yaml.replace("default: false", "default: maybe") to "FooConfig.foo_enabled: 'maybe' is not a valid Boolean",
            yaml.replace("    property: name\n", "") to "FooConfig.foo_name: missing 'property'",
            yaml.replace("type: Int\n      sdk: fooConfig", "type: Pct\n      sdk: fooConfig") to
                "FooConfig.foo_limit: remote type does not match local",
            yaml.replace("    remote:\n      type: String\n      sdk: fooName\n", "") to
                "FooConfig.foo_name: needs a local or remote section",
        ).forEach { (yaml, message) ->
            assertEquals(message, assertThrows(IllegalArgumentException::class.java) { parse(yaml) }.message)
        }
    }

    private fun parse(yaml: String) = ConfigSlice.fromYaml(yaml)
}
