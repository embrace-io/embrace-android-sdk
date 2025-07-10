package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Visits a class and adds [InstrumentationTargetMethodVisitor] to any methods that require bytecode instrumentation.
 */
class InstrumentationTargetClassVisitor(
    api: Int,
    nextClassVisitor: ClassVisitor?,
    private val feature: BytecodeInstrumentationFeature,
) : ClassVisitor(api, nextClassVisitor) {

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)

        return if (feature.targetParams.name == name && feature.targetParams.descriptor == desc && !isStatic(access)) {
            if (feature.insertionParams.insertAtEnd) {
                InstrumentationTargetMethodEndVisitor(
                    api = api,
                    methodVisitor = nextMethodVisitor,
                    params = feature.insertionParams
                )
            } else {
                InstrumentationTargetMethodVisitor(
                    api = api,
                    methodVisitor = nextMethodVisitor,
                    params = feature.insertionParams
                )
            }
        } else {
            nextMethodVisitor
        }
    }
}
