package io.embrace.bytecode.strategy

/**
 * The parts of a class that [ClassVisitStrategy] matches on.
 */
data class ClassInfo(val className: String, val superClasses: List<String>)
