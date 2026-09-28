package io.embrace.gradle.configmodel

import com.squareup.kotlinpoet.FileSpec

/**
 * The code a module generates from the config schema.
 */
enum class ConfigTarget {

    /** Everything the SDK needs: the remote config model, the instrumented config interfaces and `EmbraceConfig`. */
    SDK,

    /** The embrace-config.json model and the Gradle plugin code that instruments the SDK with it. */
    PLUGIN;

    fun render(schema: ConfigSchema, sourceName: String): List<FileSpec> = when (this) {
        SDK -> KotlinxSerializationModelRenderer(schema.packages.remote, sourceName).render(schema.remote) +
            InstrumentedConfigRenderer(schema, sourceName).render() +
            ResolvedConfigRenderer(schema, sourceName).render()
        PLUGIN -> LocalModelRenderer(schema.packages.local, sourceName).render(schema.local, schema.enums) +
            PluginInstrumentationRenderer(schema, sourceName).render()
    }
}
