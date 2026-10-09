package io.embrace.config.codegen

import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

internal object ConfigSchema {

    private val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(Fixtures.read("config.schema.json"))

    fun errors(yaml: String): List<String> =
        schema.validate(ObjectMapper().valueToTree<JsonNode>(Yaml().load<Any>(yaml))).map { it.message }
}
