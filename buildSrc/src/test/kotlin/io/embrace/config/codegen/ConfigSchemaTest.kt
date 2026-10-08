package io.embrace.config.codegen

import org.junit.Assert.assertEquals
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class ConfigSchemaTest {

    @Test
    fun `schema types match ConfigType`() {
        val all = ConfigType.entries.map { it.yamlName }
        assertEquals(all - ConfigType.PCT.yamlName, typesOf("local"))
        assertEquals(all, typesOf("remote"))
    }

    @Test
    fun `schema matches parser`() {
        listOf("foo.yaml", "nested-remote.yaml").forEach { assertEquals(it, emptyList<String>(), ConfigSchema.errors(Fixtures.read(it))) }
        Fixtures.invalidConfigs().forEach { yaml ->
            assertEquals(yaml, "# schema: invalid" in yaml.lines(), ConfigSchema.errors(yaml).isNotEmpty())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun typesOf(section: String): List<String> {
        val schema: Map<String, Any> = Yaml().load(Fixtures.read("config.schema.json"))
        val feature = (schema["definitions"] as Map<String, Any>)["feature"] as Map<String, Any>
        val property = (feature["properties"] as Map<String, Any>)[section] as Map<String, Any>
        val type = (property["properties"] as Map<String, Any>)["type"] as Map<String, Any>
        return type["enum"] as List<String>
    }
}
