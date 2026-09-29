package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Renders `EmbraceConfig`: a slice interface per feature with its defaults, a factory that falls back to them, and
 * the functions that resolve each slice from the remote config, the local config and the rollout bucket.
 */
class ResolvedConfigRenderer(
    private val schema: ConfigSchema,
    private val sourceName: String,
) {

    private val packages = schema.packages
    private val types = PoetTypes(
        schema.enums.associate { it.name to packages.instrumented } + schema.remote.classes.associate { it.name to packages.remote },
    )
    private val embraceConfig = ClassName(packages.resolved, "EmbraceConfig")
    private val configInputs = ClassName(packages.resolved, "ConfigInputs")
    private val instrumentedConfig = ClassName(packages.instrumented, "InstrumentedConfig")
    private val remoteConfig = ClassName(packages.remote, schema.remote.root.name)
    private val bucket = ClassName("kotlin", "Lazy").parameterizedBy(FLOAT)
    private val rolloutEnabled = MemberName(packages.resolved, "rolloutEnabled")

    fun render(): List<FileSpec> =
        schema.groups.map(::renderGroup) + renderEmbraceConfig() + renderInputs() + renderResolution()

    private fun sliceName(group: ConfigGroupSpec) = ClassName(packages.resolved, group.className)

    private fun defaultName(option: ConfigOptionSpec) = "DEFAULT_${screamingSnakeCase(option.name)}"

    private fun screamingSnakeCase(name: String) = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

    private fun renderGroup(group: ConfigGroupSpec): FileSpec {
        val slice = sliceName(group)
        val type = TypeSpec.interfaceBuilder(slice)
        group.doc?.let { type.addKdoc(kdoc(it)) }
        group.options.forEach { option ->
            val property = PropertySpec.builder(option.name, types.of(option.type, option.nullable))
            option.doc?.let { property.addKdoc(kdoc(it)) }
            type.addProperty(property.build())
        }
        val constants = group.options.flatMap(::constants)
        if (constants.isNotEmpty()) {
            type.addType(TypeSpec.companionObjectBuilder().addProperties(constants).build())
        }

        val implementation = TypeSpec.anonymousClassBuilder().addSuperinterface(slice)
        val factory = FunSpec.builder(group.className)
            .addModifiers(KModifier.INLINE)
            .returns(slice)
            .addKdoc("Creates a [%T]. A provider returning null means the default is used.", slice)
        group.options.forEach { option ->
            factory.addParameter(
                ParameterSpec.builder(option.name, LambdaTypeName.get(returnType = types.of(option.type, nullable = true)))
                    .addModifiers(KModifier.CROSSINLINE)
                    .defaultValue("{ null }")
                    .build(),
            )
            val value = when (option.default) {
                null -> CodeBlock.of("%N()", option.name)
                else -> CodeBlock.of("%N() ?: %T.%N", option.name, slice, defaultName(option))
            }
            implementation.addProperty(
                PropertySpec.builder(option.name, types.of(option.type, option.nullable), KModifier.OVERRIDE)
                    .initializer(value)
                    .build(),
            )
        }
        factory.addStatement("return %L", implementation.build())

        return generatedFile(packages.resolved, group.className, sourceName)
            .addType(type.build())
            .addFunction(factory.build())
            .build()
    }

    private fun constants(option: ConfigOptionSpec): List<PropertySpec> {
        val type = types.of(option.type)
        val default = option.default?.let {
            val modifiers = if (ConfigTypes.kind(option.type) in ConfigTypes.SCALARS) arrayOf(KModifier.CONST) else emptyArray()
            PropertySpec.builder(defaultName(option), type, *modifiers).initializer(types.literal(it, option.type)).build()
        }
        val range = option.remote?.clamp ?: option.remote?.validRange
        val bounds = listOfNotNull(range?.min?.let { "MIN" to it }, range?.max?.let { "MAX" to it }).map { (suffix, value) ->
            PropertySpec.builder("${screamingSnakeCase(option.name)}_$suffix", type, KModifier.CONST)
                .initializer(types.literal(value, option.type))
                .build()
        }
        return listOfNotNull(default) + bounds
    }

    private fun renderEmbraceConfig(): FileSpec {
        val constructor = FunSpec.constructorBuilder()
        val type = TypeSpec.classBuilder(embraceConfig)
            .addKdoc("Config resolved for the life of the process. Each slice is built on first read.")
        schema.groups.forEach { group ->
            val slice = sliceName(group)
            constructor.addParameter(
                ParameterSpec.builder(group.name, LambdaTypeName.get(returnType = slice)).defaultValue("{ %T() }", slice).build(),
            )
            val property = PropertySpec.builder(group.name, slice).delegate("lazy(%N)", group.name)
            group.doc?.let { property.addKdoc(kdoc(it)) }
            type.addProperty(property.build())
        }
        return generatedFile(packages.resolved, "EmbraceConfig", sourceName)
            .addType(type.primaryConstructor(constructor.build()).build())
            .build()
    }

    private fun renderInputs(): FileSpec {
        val properties = listOf(
            "local" to instrumentedConfig,
            "remote" to remoteConfig.copy(nullable = true),
            "bucket" to bucket,
        )
        val constructor = FunSpec.constructorBuilder()
        val type = TypeSpec.classBuilder(configInputs).addKdoc(
            kdoc(
                "What config is resolved from: the local config, the remote config if there is one, and the bucket that\n" +
                    "decides whether this device falls within a percentage rollout.",
            ),
        )
        properties.forEach { (name, typeName) ->
            constructor.addParameter(name, typeName)
            type.addProperty(PropertySpec.builder(name, typeName).initializer(name).build())
        }
        return generatedFile(packages.resolved, "ConfigInputs", sourceName)
            .addType(type.primaryConstructor(constructor.build()).build())
            .build()
    }

    private fun renderResolution(): FileSpec {
        val file = generatedFile(packages.resolved, "ConfigResolution", sourceName)
        val deprecated = schema.options.any { option ->
            option.resolver == null && option.remote?.let { schema.remote.resolve(it.path) }.orEmpty().any { it.deprecated != null }
        }
        if (deprecated) {
            file.addAnnotation(
                AnnotationSpec.builder(Suppress::class).useSiteTarget(UseSiteTarget.FILE).addMember("%S", "DEPRECATION").build(),
            )
        }

        val body = CodeBlock.builder()
            .addStatement("val inputs = %T(local, remote, bucket)", configInputs)
            .add("return %T(\n", embraceConfig)
            .indent()
        schema.groups.forEach { body.add("%N = { %N(inputs) },\n", it.name, resolverName(it)) }
        body.unindent().add(")\n")
        file.addFunction(
            FunSpec.builder("resolveConfig")
                .addKdoc("Resolves [%T] from the local config, the remote config and the rollout [bucket].", embraceConfig)
                .addParameter("local", instrumentedConfig)
                .addParameter("remote", remoteConfig.copy(nullable = true))
                .addParameter("bucket", bucket)
                .returns(embraceConfig)
                .addCode(body.build())
                .build(),
        )

        schema.groups.forEach { group ->
            val resolution = CodeBlock.builder().add("return with(inputs) {\n").indent().add("%T(\n", sliceName(group)).indent()
            group.options.forEach { option -> provider(option)?.let { resolution.add("%N = { %L },\n", option.name, it) } }
            resolution.unindent().add(")\n").unindent().add("}\n")
            file.addFunction(
                FunSpec.builder(resolverName(group))
                    .addKdoc("Resolves [%T] from [inputs].", sliceName(group))
                    .addParameter("inputs", configInputs)
                    .returns(sliceName(group))
                    .addCode(resolution.build())
                    .build(),
            )
        }
        return file.build()
    }

    private fun resolverName(group: ConfigGroupSpec) = "resolve${group.name.replaceFirstChar { it.uppercaseChar() }}"

    /** The expression an option is resolved from, or null if it only has a default. */
    private fun provider(option: ConfigOptionSpec): CodeBlock? {
        option.resolver?.let { return CodeBlock.of("%M(this)", MemberName(packages.resolved, it)) }
        val local = option.instrumented?.let { method ->
            val cls = schema.instrumented.single { it.name == method.className }
            CodeBlock.of("local.%N.%N()", cls.property, method.name)
        }
        val sources = listOfNotNull(option.remote?.let { remoteExpression(option, it) }, local)
        return if (sources.isEmpty()) null else sources.joinToCode(" ?: ")
    }

    private fun remoteExpression(option: ConfigOptionSpec, remote: RemoteSourceSpec): CodeBlock {
        val fields = checkNotNull(schema.remote.resolve(remote.path))
        val chain = CodeBlock.builder().add("remote")
        fields.forEach { chain.add("?.%N", it.name) }
        val value = chain.build()
        if (remote.rollout) {
            val pct = if (fields.last().type == "Float") value else CodeBlock.of("%L?.toFloat()", value)
            return CodeBlock.of("%M(%L, bucket)", rolloutEnabled, pct)
        }
        fun bound(number: Number) = types.literal(number, option.type)
        remote.clamp?.let { (min, max) ->
            return when {
                min != null && max != null -> CodeBlock.of("%L?.coerceIn(%L, %L)", value, bound(min), bound(max))
                min != null -> CodeBlock.of("%L?.coerceAtLeast(%L)", value, bound(min))
                else -> CodeBlock.of("%L?.coerceAtMost(%L)", value, bound(checkNotNull(max)))
            }
        }
        remote.validRange?.let { (min, max) ->
            return when {
                min != null && max != null -> CodeBlock.of("%L?.takeIf { it in %L..%L }", value, bound(min), bound(max))
                min != null -> CodeBlock.of("%L?.takeIf { it >= %L }", value, bound(min))
                else -> CodeBlock.of("%L?.takeIf { it <= %L }", value, bound(checkNotNull(max)))
            }
        }
        return value
    }

    private fun List<CodeBlock>.joinToCode(separator: String): CodeBlock {
        val builder = CodeBlock.builder()
        forEachIndexed { index, block ->
            if (index > 0) {
                builder.add(separator)
            }
            builder.add(block)
        }
        return builder.build()
    }
}
