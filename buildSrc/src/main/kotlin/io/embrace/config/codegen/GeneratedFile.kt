package io.embrace.config.codegen

import com.squareup.kotlinpoet.FileSpec

internal fun generatedFile(packageName: String, fileName: String, sourceName: String): FileSpec.Builder =
    FileSpec.builder(packageName, fileName)
        .indent("    ")
        .addKotlinDefaultImports()
        .addFileComment("Generated from %L. Do not edit.", sourceName)
