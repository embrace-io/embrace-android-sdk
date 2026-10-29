package io.embrace.bytecode.visitor

import org.objectweb.asm.Opcodes

/**
 * Returns true if a method is static.
 */
internal fun isStatic(access: Int) = access.and(Opcodes.ACC_STATIC) != 0
