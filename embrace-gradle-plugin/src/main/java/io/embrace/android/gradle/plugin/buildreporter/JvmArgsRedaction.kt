package io.embrace.android.gradle.plugin.buildreporter

private val ALLOWED_JVM_ARG = Regex("-Xm[xs]\\S+|-XX:[+-]Use\\w*GC")

/**
 * Keeps only heap size and GC selection flags.
 */
internal fun redactJvmArgs(jvmArgs: String): String =
    jvmArgs.split(Regex("\\s+")).filter(ALLOWED_JVM_ARG::matches).joinToString(" ")
