package io.embrace.android.gradle.plugin.instrumentation.config.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Instruments generated SDK local config classes: each getter annotated with `@LocalConfigKey`
 * returns the value at that path in embrace-config.json, or its generated default if absent.
 */
class LocalConfigClassVisitor(json: String?, api: Int, cv: ClassVisitor?) : ClassVisitor(api, cv) {

    private val root: JsonElement? by lazy { json?.let(Json::parseToJsonElement) }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return LocalConfigMethodVisitor(descriptor, ::valueAt, api, visitor)
    }

    private fun valueAt(path: String): JsonPrimitive? {
        val node = path.split('.').fold(root) { node, key -> (node as? JsonObject)?.get(key) }
        return (node as? JsonPrimitive)?.takeUnless { it is JsonNull }
    }
}
