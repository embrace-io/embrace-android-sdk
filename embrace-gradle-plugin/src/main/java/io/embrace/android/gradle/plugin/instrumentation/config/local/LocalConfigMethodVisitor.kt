package io.embrace.android.gradle.plugin.instrumentation.config.local

import io.embrace.android.gradle.plugin.instrumentation.config.BooleanReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.IntReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.LongReturnValueMethodVisitor
import io.embrace.android.gradle.plugin.instrumentation.config.StringReturnValueMethodVisitor
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.MethodVisitor

private const val LOCAL_CONFIG_KEY = "Lio/embrace/android/embracesdk/internal/config/instrumented/LocalConfigKey;"

/**
 * Replaces a getter's return value with the value at its `@LocalConfigKey` path, if set. Annotations
 * are visited before code, so the path is known by [visitCode].
 */
class LocalConfigMethodVisitor(
    private val descriptor: String,
    private val valueAt: (String) -> JsonPrimitive?,
    api: Int,
    visitor: MethodVisitor,
) : MethodVisitor(api, visitor) {

    private var path: String? = null

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val visitor = super.visitAnnotation(descriptor, visible)
        return if (descriptor != LOCAL_CONFIG_KEY) {
            visitor
        } else {
            object : AnnotationVisitor(api, visitor) {
                override fun visit(name: String?, value: Any?) {
                    path = value as? String
                    super.visit(name, value)
                }
            }
        }
    }

    override fun visitCode() {
        val path = path
        val value = path?.let(valueAt)
        if (value != null) {
            mv = when (descriptor) {
                "()Z" -> value.booleanOrNull?.let { BooleanReturnValueMethodVisitor(it, api, mv) }
                "()I" -> value.intOrNull?.let { IntReturnValueMethodVisitor(it, api, mv) }
                "()J" -> value.longOrNull?.let { LongReturnValueMethodVisitor(it, api, mv) }
                "()Ljava/lang/String;" -> value.takeIf { it.isString }?.let { StringReturnValueMethodVisitor(it.content, api, mv) }
                else -> null
            } ?: throw IllegalArgumentException("embrace-config.json: '$path' is not a valid value for $descriptor")
        }
        super.visitCode()
    }
}
