package io.embrace.android.embracesdk.internal.config

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal class PatternCache {

    private companion object {

        /**
         * Callers use a small number of fixed pattern sets sourced from config, so the cache
         * should never grow unbounded.
         */
        private const val MAX_CACHED_PATTERN_SETS = 1000
    }

    private val memo: MutableMap<Collection<String>, Collection<Pattern>> = ConcurrentHashMap()

    fun doesStringMatchPatternInSet(string: String, patterns: Collection<String>): Boolean =
        compiledPatterns(patterns).any { it.matcher(string).matches() }

    fun doesStringContainMatchInSet(string: String, patterns: Collection<String>): Boolean =
        compiledPatterns(patterns).any { it.matcher(string).find() }

    private fun compiledPatterns(patterns: Collection<String>): Collection<Pattern> =
        memo[patterns] ?: compilePatterns(patterns).also {
            if (memo.size < MAX_CACHED_PATTERN_SETS) {
                memo[patterns] = it
            }
        }

    private fun compilePatterns(patterns: Collection<String>): Collection<Pattern> =
        patterns.toSet().mapNotNull {
            runCatching { Pattern.compile(it) }.getOrNull()
        }
}
