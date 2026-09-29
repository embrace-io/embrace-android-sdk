package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

/**
 * Maps config schema types onto KotlinPoet types. [packages] gives the package of each enum and class a type may
 * name, which differs between the SDK and the Gradle plugin.
 */
class PoetTypes(private val packages: Map<String, String>) {

    private companion object {
        val SCALARS = mapOf("Boolean" to BOOLEAN, "Int" to INT, "Long" to LONG, "Float" to FLOAT, "Double" to DOUBLE, "String" to STRING)
    }

    fun className(name: String): ClassName = ClassName(checkNotNull(packages[name]) { "No package for $name" }, name)

    fun of(type: String, nullable: Boolean = false): TypeName {
        val element = checkNotNull(ConfigTypes.element(type)) { "Malformed type $type" }
        val elementType = SCALARS[element] ?: className(element)
        val typeName = when (ConfigTypes.kind(type)) {
            "List" -> LIST.parameterizedBy(elementType)
            "Set" -> SET.parameterizedBy(elementType)
            "Map" -> MAP.parameterizedBy(STRING, elementType)
            else -> elementType
        }
        return typeName.copy(nullable = nullable)
    }

    /** Renders a value that passed [ConfigTypes.isValidDefault] as a Kotlin expression of [type]. */
    fun literal(value: Any?, type: String): CodeBlock = if (value == null) CodeBlock.of("null") else when (val kind = ConfigTypes.kind(type)) {
        "Long" -> CodeBlock.of("%LL", value)
        "Float" -> CodeBlock.of("%Lf", (value as Number).toDouble())
        "Double" -> CodeBlock.of("%L", (value as Number).toDouble())
        "String" -> CodeBlock.of("%S", value)
        "List", "Set", "Map" -> CodeBlock.of("%M()", MemberName("kotlin.collections", "empty$kind"))
        "Int", "Boolean" -> CodeBlock.of("%L", value)
        else -> CodeBlock.of("%T.%N", className(type), value)
    }
}

/** Prepares text for KDoc: a comment terminator or opener would otherwise end or nest the comment. */
fun kdoc(text: String): CodeBlock =
    CodeBlock.of("%L", text.trim().replace("*/", "*&#47;").replace("/*", "&#47;*"))

/** A file with the header every generated file carries. */
fun generatedFile(packageName: String, name: String, sourceName: String): FileSpec.Builder =
    FileSpec.builder(packageName, name)
        .indent("    ")
        .addFileComment("Generated from %L. Do not edit: change %L and rebuild instead.", sourceName, sourceName)
