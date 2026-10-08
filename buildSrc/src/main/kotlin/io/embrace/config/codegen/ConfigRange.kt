package io.embrace.config.codegen

import com.squareup.kotlinpoet.CodeBlock

/**
 * Valid value ranges: [bounds] for an Int or Long, [maxLength] for a String.
 */
class ConfigRange(val bounds: LongRange?, val maxLength: Int?) {

    fun contains(value: String): Boolean = bounds?.contains(value.toLong()) ?: (value.length <= checkNotNull(maxLength))

    fun condition(type: ConfigType): CodeBlock = when (bounds) {
        null -> CodeBlock.of("it.length <= %L", maxLength)
        else -> CodeBlock.of("it in %L..%L", type.literal("${bounds.first}"), type.literal("${bounds.last}"))
    }
}
