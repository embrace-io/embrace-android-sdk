package io.embrace.android.gradle.plugin.instrumentation.strategy

/**
 * The parts of a class that [ClassVisitStrategy] matches on.
 */
data class ClassInfo(val className: String, val superClasses: List<String>)
