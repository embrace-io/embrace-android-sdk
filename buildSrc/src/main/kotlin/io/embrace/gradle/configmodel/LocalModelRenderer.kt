package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Renders the embrace-config.json model and its enums, annotated for both Moshi and kotlinx.serialization. The
 * classes are [java.io.Serializable] because Gradle serializes them as task inputs.
 */
class LocalModelRenderer(
    private val packageName: String,
    private val sourceName: String,
) {

    private companion object {
        val JSON = ClassName("com.squareup.moshi", "Json")
        val JSON_CLASS = ClassName("com.squareup.moshi", "JsonClass")
        val JAVA_SERIALIZABLE = ClassName("java.io", "Serializable")
    }

    fun render(model: ConfigModelSpec, enums: List<ConfigEnumSpec>): List<FileSpec> {
        val types = PoetTypes((model.classes.map { it.name } + enums.map { it.name }).associateWith { packageName })
        val builder = ModelClassBuilder(types) {
            listOf(
                AnnotationSpec.builder(JSON).addMember("name = %S", it.key).build(),
                KotlinxSerializationModelRenderer.serialName(it.key),
            )
        }
        val classes = model.classes.map { cls ->
            val type = builder.build(cls)
                .addAnnotation(AnnotationSpec.builder(JSON_CLASS).addMember("generateAdapter = true").build())
                .addAnnotation(KotlinxSerializationModelRenderer.SERIALIZABLE)
                .addSuperinterface(JAVA_SERIALIZABLE)
                .addType(serialVersionUid())
            generatedFile(packageName, cls.name, sourceName).addType(type.build()).build()
        }
        return classes + enums.map { EnumRenderer.render(it, packageName, sourceName, local = true) }
    }

    private fun serialVersionUid(): TypeSpec = TypeSpec.companionObjectBuilder()
        .addProperty(
            PropertySpec.builder("serialVersionUID", LONG, KModifier.PRIVATE, KModifier.CONST)
                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "ConstPropertyName").build())
                .initializer("1L")
                .build(),
        )
        .build()
}
