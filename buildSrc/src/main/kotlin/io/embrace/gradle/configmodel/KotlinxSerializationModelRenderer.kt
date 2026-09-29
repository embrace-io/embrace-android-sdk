package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec

/**
 * Renders the remote config model as kotlinx.serialization data classes.
 */
class KotlinxSerializationModelRenderer(
    private val packageName: String,
    private val sourceName: String,
) {

    fun render(model: ConfigModelSpec): List<FileSpec> {
        val types = PoetTypes(model.classes.associate { it.name to packageName })
        val builder = ModelClassBuilder(types) { listOf(serialName(it.key)) }
        return model.classes.map { cls ->
            generatedFile(packageName, cls.name, sourceName)
                .addType(builder.build(cls).addAnnotation(SERIALIZABLE).build())
                .build()
        }
    }

    companion object {
        val SERIALIZABLE: ClassName = ClassName("kotlinx.serialization", "Serializable")
        private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

        fun serialName(key: String): AnnotationSpec = AnnotationSpec.builder(SERIAL_NAME).addMember("%S", key).build()
    }
}
