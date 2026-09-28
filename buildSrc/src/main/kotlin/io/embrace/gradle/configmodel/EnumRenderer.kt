package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Renders a [ConfigEnumSpec]. A [local] enum is part of the embrace-config.json model, so each value is annotated
 * with its JSON name for Moshi and kotlinx.serialization.
 */
object EnumRenderer {

    private val JSON = ClassName("com.squareup.moshi", "Json")

    fun render(enum: ConfigEnumSpec, packageName: String, sourceName: String, local: Boolean): FileSpec {
        val type = TypeSpec.enumBuilder(enum.name)
        enum.doc?.let { type.addKdoc(kdoc(it)) }
        enum.values.forEach { value ->
            val constant = TypeSpec.anonymousClassBuilder()
            value.doc?.let { constant.addKdoc(kdoc(it)) }
            if (local) {
                constant.addAnnotation(AnnotationSpec.builder(JSON).addMember("name = %S", value.json).build())
                constant.addAnnotation(KotlinxSerializationModelRenderer.serialName(value.json))
            }
            type.addEnumConstant(value.name, constant.build())
        }
        return generatedFile(packageName, enum.name, sourceName).addType(type.build()).build()
    }
}
