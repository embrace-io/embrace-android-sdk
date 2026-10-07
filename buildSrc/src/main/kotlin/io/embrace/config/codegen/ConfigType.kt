package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.BOOLEAN as BOOLEAN_TYPE
import com.squareup.kotlinpoet.FLOAT as FLOAT_TYPE
import com.squareup.kotlinpoet.INT as INT_TYPE
import com.squareup.kotlinpoet.LONG as LONG_TYPE
import com.squareup.kotlinpoet.STRING as STRING_TYPE

enum class ConfigType(val typeName: ClassName, private val format: String, private val valid: Regex) {
    BOOLEAN(BOOLEAN_TYPE, "%L", Regex("true|false")),
    INT(INT_TYPE, "%L", Regex("-?\\d+")),
    LONG(LONG_TYPE, "%LL", Regex("-?\\d+")),
    STRING(STRING_TYPE, "%S", Regex(".*")),
    PCT(FLOAT_TYPE, "%Lf", Regex("\\d+(\\.\\d+)?"));

    val yamlName: String = name.lowercase().replaceFirstChar(Char::uppercaseChar)

    val resolvedType: ConfigType get() = when (PCT) {
        this -> BOOLEAN
        else -> this
    }

    fun literal(value: String): CodeBlock {
        require(valid.matches(value)) { "'$value' is not a valid $yamlName" }
        return CodeBlock.of(format, value)
    }

    companion object {
        fun fromYaml(name: String): ConfigType = entries.find { it.yamlName == name }
            ?: throw IllegalArgumentException("unknown type '$name'")
    }
}
