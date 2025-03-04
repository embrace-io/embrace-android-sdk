package io.embrace.android.gradle.integration.framework.smali

import java.io.File

class SmaliParser {

    private companion object {
        private const val METHOD_START = ".method"
        private const val METHOD_END = ".end method"
        private const val STRING_CONSTANT = "const-string"
        private const val CONSTRUCTOR = "<init>()V"
        private const val CLASS_CONSTRUCTOR = "<clinit>()V"
    }

    /**
     * Parses a smali file and returns a list of method representations. This parser
     * is fairly naive and makes various assumptions about smali, but this is sufficient for
     * testing purposes right now given that our bytecode instrumentation is fairly simple.
     */
    fun parse(file: File): SmaliFile {
        val methods = mutableMapOf<String, String?>()
        var methodSig: String? = null
        var returnValue: String? = null

        file.inputStream().bufferedReader().lines().forEach { line ->
            val input = line.trim()
            if (input.startsWith(METHOD_START) && !input.contains(CLASS_CONSTRUCTOR) && !input.contains(CONSTRUCTOR)) {
                if (methodSig != null || returnValue != null) {
                    error("Expected null methodSig + returnValue: $input")
                }
                methodSig = input.split(" ").last()
            }
            if (input.startsWith(METHOD_END)) {
                if (methodSig != null) {
                    methods[checkNotNull(methodSig)] = returnValue
                }
                methodSig = null
                returnValue = null
            }
            if (input.startsWith(STRING_CONSTANT)) {
                returnValue = input.split(" ").last().replace("\"", "")
            }
        }
        return SmaliFile(file.nameWithoutExtension, methods.map { SmaliMethod(it.key, it.value) })
    }
}
