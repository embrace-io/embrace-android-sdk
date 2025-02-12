package io.embrace.android.gradle.plugin.instrumentation.visitor

import org.objectweb.asm.Opcodes

/**
 * Returns true if a method is static.
 */
internal fun isStatic(access: Int) = access.and(Opcodes.ACC_STATIC) != 0

/**
 * Returns true if a method is synthetic (e.g. a Java lambda).
 */
internal fun isSynthetic(access: Int) = access.and(Opcodes.ACC_SYNTHETIC) != 0
