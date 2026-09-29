package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

/**
 * Renders the Gradle plugin code that maps embrace-config.json onto the SDK's instrumented config methods: a
 * `create<Class>Instrumentation` function per instrumented class, and a function that picks one by class name.
 */
class PluginInstrumentationRenderer(
    private val schema: ConfigSchema,
    private val sourceName: String,
) {

    private val packages = schema.packages
    private val enumNames = schema.enums.map { it.name }.toSet()
    private val instrumentedConfigClass = ClassName(packages.pluginDsl, "InstrumentedConfigClass")
    private val modelSdkConfigClass = MemberName(packages.pluginDsl, "modelSdkConfigClass")
    private val dslMethods = mapOf(
        "Boolean" to "boolMethod",
        "Int" to "intMethod",
        "Long" to "longMethod",
        "String" to "stringMethod",
        "List<String>" to "stringListMethod",
        "Map<String, String>" to "mapMethod",
    ).mapValues { MemberName(packages.pluginDsl, it.value, isExtension = true) }
    private val enumMethod = MemberName(packages.pluginDsl, "enumMethod", isExtension = true)
    private val paramTypes: Map<String, TypeName> = mapOf(
        "cfg" to ClassName(packages.local, "VariantConfig"),
        "encodedSharedObjectFilesMap" to STRING.copy(nullable = true),
        "reactNativeBundleId" to STRING.copy(nullable = true),
        "variantOutputInfo" to ClassName("io.embrace.android.gradle.plugin.model", "VariantOutputInfo"),
    )

    /** Only classes with a value to write are instrumented; the rest keep their defaults. */
    private val instrumentedClasses = schema.instrumented.filter { cls -> methods(cls).isNotEmpty() }

    fun render(): List<FileSpec> = instrumentedClasses.map(::renderClass) + renderDispatcher()

    private fun methods(cls: InstrumentedClassSpec) =
        schema.instrumentedMethods(cls).filter { (option, _) -> option.localPath != null || option.pluginValue != null }

    private fun renderClass(cls: InstrumentedClassSpec): FileSpec {
        val body = CodeBlock.builder().add("return %M {\n", modelSdkConfigClass).indent()
        methods(cls).forEach { (option, method) ->
            val value = option.pluginValue?.let(::expression) ?: localChain(option, method)
            when (method.type) {
                in enumNames -> body.add(
                    "%M(%S, %S) { %L }\n",
                    enumMethod,
                    method.name,
                    "${packages.instrumented}.${method.type}",
                    value,
                )
                else -> body.add("%M(%S) { %L }\n", dslMethods.getValue(method.type), method.name, value)
            }
        }
        body.unindent().add("}\n")
        val function = FunSpec.builder("create${cls.name}Instrumentation")
            .addKdoc("Maps embrace-config.json onto the SDK's `%L`.", cls.name)
            .returns(instrumentedConfigClass)
            .addCode(body.build())
        cls.pluginParams.forEach { function.addParameter(it, paramTypes.getValue(it)) }
        return generatedFile(packages.plugin, "${cls.name}Instrumentation", sourceName).addFunction(function.build()).build()
    }

    /** A Kotlin expression from the schema. Its spaces are made non-breaking so that line wrapping can't split it. */
    private fun expression(text: String): CodeBlock = CodeBlock.of(text.replace("%", "%%").replace(' ', '·'))

    private fun localChain(option: ConfigOptionSpec, method: InstrumentedMethodSpec): CodeBlock {
        val fields = checkNotNull(schema.local.resolve(checkNotNull(option.localPath)))
        val chain = CodeBlock.builder().add("cfg.embraceConfig")
        fields.forEach { chain.add("?.%N", it.name) }
        if (method.type in enumNames) {
            chain.add("?.name")
        }
        return chain.build()
    }

    private fun renderDispatcher(): FileSpec {
        val body = CodeBlock.builder().beginControlFlow("return when (className)")
        instrumentedClasses.forEach { cls ->
            val args = cls.pluginParams.joinToString(", ")
            body.addStatement("%S -> create${cls.name}Instrumentation($args)", "${packages.instrumentedImpl}.${cls.name}Impl")
        }
        body.addStatement("else -> null").endControlFlow()

        val function = FunSpec.builder("createConfigInstrumentation")
            .addKdoc("Creates the instrumentation for an SDK config class, or returns null if [className] is not one.")
            .addParameter("className", STRING)
            .returns(instrumentedConfigClass.copy(nullable = true))
            .addCode(body.build())
        paramTypes.forEach { (name, type) -> function.addParameter(name, type) }
        if (paramTypes.keys.any { param -> instrumentedClasses.none { param in it.pluginParams } }) {
            function.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNUSED_PARAMETER").build())
        }
        return generatedFile(packages.plugin, "ConfigInstrumentation", sourceName).addFunction(function.build()).build()
    }
}
