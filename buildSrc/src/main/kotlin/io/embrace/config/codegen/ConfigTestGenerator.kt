package io.embrace.config.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates a unit test for a resolved config that checks each field's default and its remote/local/default
 * precedence. Local values are injected via `overrideLocal()`, which must exist in the test source set.
 *
 * These only prove the generated plumbing is sound, not that a value means the right thing to the SDK. Domain-specific
 * behaviour belongs in a hand-written `<Slice>Test` alongside the generated `<Slice>GeneratedTest`.
 */
fun configTestFile(slice: ConfigSlice, packageName: String, sourceName: String): FileSpec {
    val tests = slice.fields.flatMap { field ->
        try {
            ConfigTestCases(slice, field, packageName).tests()
        } catch (exc: IllegalArgumentException) {
            throw IllegalArgumentException("${slice.name}.${field.name}: ${exc.message}", exc)
        }
    }
    val type = TypeSpec.classBuilder("${slice.name}GeneratedTest")
        .addModifiers(KModifier.INTERNAL)
        .addFunctions(tests)
    return generatedFile(packageName, "${slice.name}GeneratedTest", sourceName)
        .addType(type.build())
        .build()
}
