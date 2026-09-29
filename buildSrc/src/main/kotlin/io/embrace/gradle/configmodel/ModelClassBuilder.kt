package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Builds the data class for a JSON model class, annotating each property with [keyAnnotations] for its JSON key.
 */
class ModelClassBuilder(
    private val types: PoetTypes,
    private val keyAnnotations: (ConfigFieldSpec) -> List<AnnotationSpec>,
) {

    fun build(cls: ConfigClassSpec): TypeSpec.Builder {
        val constructor = FunSpec.constructorBuilder()
        val builder = TypeSpec.classBuilder(cls.name).addModifiers(KModifier.DATA)
        cls.doc?.let { builder.addKdoc(kdoc(it)) }
        cls.fields.forEach { field ->
            val type = types.of(field.type, field.nullable)
            val parameter = ParameterSpec.builder(field.name, type)
            when {
                field.default != null -> parameter.defaultValue(types.literal(field.default, field.type))
                field.nullable && !field.required -> parameter.defaultValue("null")
            }
            constructor.addParameter(parameter.build())

            val property = PropertySpec.builder(field.name, type).initializer(field.name)
            field.doc?.let { property.addKdoc(kdoc(it)) }
            field.deprecated?.let { property.addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", it).build()) }
            builder.addProperty(property.addAnnotations(keyAnnotations(field)).build())
        }
        return builder.primaryConstructor(constructor.build())
    }
}
