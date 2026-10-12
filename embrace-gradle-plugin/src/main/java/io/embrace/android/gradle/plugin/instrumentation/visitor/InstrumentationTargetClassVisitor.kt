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

    private var className: String? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?,
    ) {
        className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<String>?,
    ): MethodVisitor? {
        val nextMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
        val target = feature.targetParams
        val descriptorMatches = target.descriptor == null || target.descriptor == desc

        return if (target.name == name && descriptorMatches && !isStatic(access)) {
            if (name == "<init>") {
                InstrumentationTargetConstructorVisitor(
                    api = api,
                    methodVisitor = nextMethodVisitor,
                    className = checkNotNull(className),
                    params = feature.insertionParams,
                )
            } else if (feature.insertionParams.insertAtEnd) {
                InstrumentationTargetMethodEndVisitor(
                    api = api,
                    methodVisitor = nextMethodVisitor,
                    params = feature.insertionParams,
                )
            } else {
                InstrumentationTargetMethodVisitor(
                    api = api,
                    methodVisitor = nextMethodVisitor,
                    params = feature.insertionParams,
                )
            }
        } else {
            nextMethodVisitor
        }
    }
}
