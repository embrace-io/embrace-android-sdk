package io.embrace.android.gradle.integration.framework.smali

import java.io.File

class SmaliParser {

    private companion object {
        private const val METHOD_START = ".method"
        private const val METHOD_END = ".end method"
        private const val METHOD_ARGS = ")"
        private const val STRING_CONSTANT = "const-string"
        private const val LOW_INT_CONSTANT = "const/16"
        private const val BOOL_CONSTANT = "const/4"
        private const val CONSTRUCTOR = "<init>()V"
        private const val CLASS_CONSTRUCTOR = "<clinit>()V"
        private const val RETURN_TYPE_STRING = "Ljava/lang/String;"
        private const val RETURN_TYPE_INT = "I"
        private const val RETURN_TYPE_BOOL = "Z"
        private const val RETURN_TYPE_LIST = "Ljava/util/List;"
        private const val RETURN_TYPE_MAP = "Ljava/util/Map;"
        private const val INVOKE_STATIC = "invoke-static"
        private const val INSTRUMENTATION_MARKER = "io/embrace/"
        private const val DEFAULT_IMPLS_MARKER = "DefaultImpls"
    }

    /**
     * Parses a smali file and returns a list of method representations. This parser
     * is fairly naive and makes various assumptions about smali, but this is sufficient for
     * testing purposes right now given that our bytecode instrumentation is fairly simple.
     */
    fun parse(file: File, expectedMethods: List<SmaliMethod>): SmaliFile {
        val methods = mutableListOf<SmaliMethod>()
        var methodSig: String? = null
        var returnValue: String? = null
        var invokeStatic: String? = null

        file.inputStream().bufferedReader().lines().forEach { line ->
            val input = line.trim()
            if (isMethodStart(input)) {
                if (methodSig != null || returnValue != null) {
                    error("Expected null methodSig + returnValue: $input")
                }
                methodSig = input.split(" ").last()
            } else if (isMethodEnd(input)) {
                if (methodSig != null && expectedMethods.any { it.signature == methodSig }) {
                    methods.add(SmaliMethod(checkNotNull(methodSig), returnValue, invokeStatic))
                }
                methodSig = null
                returnValue = null
                invokeStatic = null
            } else if (isEmbraceInstrumentedCode(input, invokeStatic)) {
                // only look at first static invocation, as that should always be an embrace-added hook
                invokeStatic = input
            } else if (methodSig != null) {
                val returnType = checkNotNull(methodSig).split(METHOD_ARGS).last()
                val value = readReturnValue(input, returnType)
                if (value != null) {
                    returnValue = computeReturnValue(returnValue, value, returnType)
                }
            }
        }
        return SmaliFile(file.nameWithoutExtension, methods)
    }

    private fun isMethodStart(input: String): Boolean {
        return input.startsWith(METHOD_START) && !input.contains(CLASS_CONSTRUCTOR) && !input.contains(CONSTRUCTOR)
    }

    private fun isMethodEnd(input: String): Boolean {
        return input.startsWith(METHOD_END)
    }

    private fun isEmbraceInstrumentedCode(input: String, invokeStatic: String?): Boolean {
        return input.startsWith(INVOKE_STATIC) && invokeStatic == null && input.contains(INSTRUMENTATION_MARKER) &&
            !input.contains(DEFAULT_IMPLS_MARKER)
    }

    private fun readReturnValue(input: String, returnType: String): String? {
        return if (input.startsWith(STRING_CONSTANT) && returnType == RETURN_TYPE_STRING) {
            input.split(" ").last().replace("\"", "")
        } else if (input.startsWith(LOW_INT_CONSTANT) && returnType == RETURN_TYPE_INT) {
            readHexValue(input)
        } else if (input.startsWith(BOOL_CONSTANT) && returnType == RETURN_TYPE_BOOL) {
            when (readHexValue(input)) {
                "1" -> "true"
                "0" -> "false"
                else -> error("Unexpected boolean value")
            }
        } else if (input.startsWith(STRING_CONSTANT) && (returnType == RETURN_TYPE_LIST || returnType == RETURN_TYPE_MAP)) {
            input.split(" ").last().replace("\"", "")
        } else {
            null
        }
    }

    private fun computeReturnValue(returnValue: String?, value: String, returnType: String): String {
        if (returnValue == null) {
            return value
        }

        val separator = when (returnType) {
            RETURN_TYPE_LIST -> ","
            RETURN_TYPE_MAP -> ":"
            else -> null
        }
        return when (separator) {
            null -> value
            else -> "$returnValue$separator$value"
        }
    }

    private fun readHexValue(input: String): String {
        val token = input.split(" ").last()
        val value = token.replace("\"", "").replace("0x", "")
        return value.toInt(16).toString()
    }
}
