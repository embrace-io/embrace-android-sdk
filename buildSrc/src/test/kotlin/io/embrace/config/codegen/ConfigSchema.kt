package io.embrace.config.codegen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.yaml.snakeyaml.Yaml

internal object ConfigSchema {

    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(Fixtures.read("config.schema.json"))

    fun errors(yaml: String): List<String> =
        schema.validate(ObjectMapper().valueToTree<JsonNode>(Yaml().load<Any>(yaml))).map { it.message }
}
