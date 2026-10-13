package io.embrace.config.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates a config's local config interface and an object with defaults. The Gradle plugin
 * instruments these with values from embrace-config.json.
 */
fun localConfigFiles(slice: ConfigSlice, sourceName: String): List<FileSpec> {
    if (slice.localFields.isEmpty()) {
        return emptyList()
    }
    val iface = TypeSpec.interfaceBuilder(slice.localConfig)
        .addKdoc("Local %L, read from embrace-config.json.", slice.doc.removePrefix("Resolved ").removeSuffix("."))
        .addFunctions(
            slice.localFields.map {
                FunSpec.builder(it.localGetter).addModifiers(KModifier.ABSTRACT).returns(it.type.typeName).build()
            },
        )
    val impl = TypeSpec.objectBuilder(slice.localConfigImpl)
        .addKdoc("Instrumented by the Embrace Gradle plugin to return the values set in embrace-config.json.")
        .addAnnotation(ConfigClassNames.LOCAL_CONFIG_INSTRUMENTED)
        .addSuperinterface(slice.localConfig)
        .addFunctions(
            slice.localFields.map {
                FunSpec.builder(it.localGetter)
                    .addAnnotation(
                        AnnotationSpec.builder(ConfigClassNames.LOCAL_CONFIG_KEY).addMember("%S", checkNotNull(it.local).json).build(),
                    )
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(it.type.typeName)
                    .addStatement("return %L", it.type.literal(it.default))
                    .build()
            },
        )
    return listOf(
        generatedFile(slice.localConfig.packageName, slice.localConfig.simpleName, sourceName).addType(iface.build()).build(),
        generatedFile(slice.localConfigImpl.packageName, slice.localConfigImpl.simpleName, sourceName).addType(impl.build()).build(),
    )
}
