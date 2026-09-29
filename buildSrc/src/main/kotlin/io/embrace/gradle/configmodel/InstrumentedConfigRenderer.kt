package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Renders the SDK interfaces that the Gradle plugin instruments with local config, the objects it rewrites, and the
 * enums they return. Each method returns its default until the plugin replaces the value.
 */
class InstrumentedConfigRenderer(
    private val schema: ConfigSchema,
    private val sourceName: String,
) {

    private val packages = schema.packages
    private val types = PoetTypes(schema.enums.associate { it.name to packages.instrumented })
    private val instrumentedConfig = ClassName(packages.instrumented, "InstrumentedConfig")
    private val embraceInstrumented = ClassName(packages.instrumentedImpl, "EmbraceInstrumented")

    fun render(): List<FileSpec> =
        schema.instrumented.map(::renderInterface) +
            renderInstrumentedConfig() +
            renderImpl() +
            schema.enums.map { EnumRenderer.render(it, packages.instrumented, sourceName, local = false) }

    private fun className(cls: InstrumentedClassSpec) = ClassName(packages.instrumented, cls.name)

    private fun renderInterface(cls: InstrumentedClassSpec): FileSpec {
        val type = TypeSpec.interfaceBuilder(className(cls))
        cls.doc?.let { type.addKdoc(kdoc(it)) }
        schema.instrumentedMethods(cls).forEach { (option, method) ->
            val function = FunSpec.builder(method.name)
                .returns(types.of(method.type, method.nullable))
                .addStatement("return %L", types.literal(method.default, method.type))
            val doc = listOfNotNull(option.doc?.trim(), option.localPath).joinToString("\n\n")
            if (doc.isNotEmpty()) {
                function.addKdoc(kdoc(doc))
            }
            type.addFunction(function.build())
        }
        return generatedFile(packages.instrumented, cls.name, sourceName).addType(type.build()).build()
    }

    private fun renderInstrumentedConfig(): FileSpec {
        val type = TypeSpec.interfaceBuilder(instrumentedConfig).addKdoc(
            kdoc(
                "Defines the locally set configuration for the SDK. This is typically set from embrace-config.json\n" +
                    "and instrumented by the embrace gradle plugin, but can be overridden for test purposes.",
            ),
        )
        schema.instrumented.forEach { type.addProperty(it.property, className(it)) }
        return generatedFile(packages.instrumented, "InstrumentedConfig", sourceName).addType(type.build()).build()
    }

    private fun renderImpl(): FileSpec {
        val impl = TypeSpec.objectBuilder("InstrumentedConfigImpl")
            .addAnnotation(embraceInstrumented)
            .addSuperinterface(instrumentedConfig)
            .addKdoc(
                kdoc(
                    "This class and its contents are instrumented by the embrace gradle plugin to alter its return values\n" +
                        "based on what values have been set in the embrace-config.json. If no value has been set,\n" +
                        "the default value specified in the class will be used.",
                ),
            )
        schema.instrumented.forEach {
            impl.addProperty(
                PropertySpec.builder(it.property, className(it), KModifier.OVERRIDE)
                    .initializer("%T", ClassName(packages.instrumentedImpl, "${it.name}Impl"))
                    .build(),
            )
        }
        val file = generatedFile(packages.instrumentedImpl, "InstrumentedConfigImpl", sourceName).addType(impl.build())
        schema.instrumented.forEach {
            file.addType(
                TypeSpec.objectBuilder("${it.name}Impl")
                    .addAnnotation(embraceInstrumented)
                    .addSuperinterface(className(it))
                    .build(),
            )
        }
        return file.build()
    }
}
