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

    private val memo: MutableMap<Set<String>, Collection<Pattern>> = ConcurrentHashMap()

    fun doesStringMatchPatternInSet(string: String, patternSet: Set<String>): Boolean =
        compiledPatterns(patternSet).any { it.matcher(string).matches() }

    fun doesStringContainMatchInSet(string: String, patternSet: Set<String>): Boolean =
        compiledPatterns(patternSet).any { it.matcher(string).find() }

    private fun compiledPatterns(patternSet: Set<String>): Collection<Pattern> =
        memo[patternSet] ?: compilePatterns(patternSet).also {
            if (memo.size < MAX_CACHED_PATTERN_SETS) {
                memo[patternSet] = it
            }
        }

    private fun compilePatterns(patternSet: Set<String>): Collection<Pattern> =
        patternSet.mapNotNull {
            runCatching { Pattern.compile(it) }.getOrNull()
        }
}
