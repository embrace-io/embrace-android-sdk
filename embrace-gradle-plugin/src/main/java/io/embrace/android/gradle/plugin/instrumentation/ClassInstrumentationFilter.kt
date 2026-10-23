package io.embrace.android.gradle.plugin.instrumentation

import java.io.Serializable
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A filter that determines whether a class should be skipped, according to user-defined rules.
 */
class ClassInstrumentationFilter(
    internal val skipList: List<String>,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }

    init {
        skipList.forEach { regex ->
            try {
                Pattern.compile(regex)
            } catch (exc: PatternSyntaxException) {
                throw IllegalArgumentException(
                    "Invalid regex '$regex' in embrace.bytecodeInstrumentation.classIgnorePatterns: ${exc.description}",
                    exc,
                )
            }
        }
    }

    private val skipPatterns by lazy { skipList.map(Pattern::compile) }

    /**
     * @param name the class name
     * @return if the given name should be skipped or not.
     */
    fun shouldSkip(name: String): Boolean = skipPatterns.any { pattern ->
        pattern.matcher(name).find()
    }
}
