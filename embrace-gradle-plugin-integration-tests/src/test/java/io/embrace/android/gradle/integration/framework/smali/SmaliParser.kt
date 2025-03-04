package io.embrace.android.gradle.integration.framework.smali

import java.io.File

class SmaliParser {

    private companion object {
        private const val METHOD_START = ".method"
        private const val METHOD_END = ".end method"
        private const val STRING_CONSTANT = "const-string"
    }

    /**
     * Parses a smali file and returns a list of method representations. This parser
     * is fairly naive and makes various assumptions about smali, but this is sufficient for
     * testing purposes right now given that our bytecode instrumentation is fairly simple.
     */
    fun parse(file: File): List<SmaliMethod> {
        val methods = mutableMapOf<String, String>()
        var methodSig: String? = null
        var returnValue: String? = null

        file.inputStream().bufferedReader().lines().forEach { line ->
            val input = line.trim()
            if (input.startsWith(METHOD_START)) {
                if (methodSig != null || returnValue != null) {
                    error("Expected null methodSig + returnValue: $input")
                }
                methodSig = input.split(" ").last()
            }
            if (input.startsWith(METHOD_END)) {
                if (methodSig != null && returnValue != null) {
                    methods[checkNotNull(methodSig)] = checkNotNull(returnValue)
                }
                methodSig = null
                returnValue = null
            }
            if (input.startsWith(STRING_CONSTANT)) {
                returnValue = input.split(" ").last().replace("\"", "")
            }
        }
        return methods.map { SmaliMethod(it.key, it.value) }
    }
}
