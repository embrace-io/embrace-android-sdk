package io.embrace.android.gradle.plugin.gradle

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider

/**
 * Workaround that creates an explicit anon inner class so that configuration cache isn't broken. The transform may return null to produce
 * an empty provider, matching Gradle's contract.
 */
@Suppress("ObjectLiteralToLambda")
inline fun <O : Any, I : Any> Provider<I>.safeMap(
    crossinline transform: (I) -> O?,
): Provider<O> {
    return map(
        object : Transformer<O?, I> {
            override fun transform(input: I): O? {
                return transform(input)
            }
        }
    )
}

/**
 * Workaround that creates an explicit anon inner class so that configuration cache isn't broken.
 */
@Suppress("ObjectLiteralToLambda")
inline fun <T : Any, R : Any> Provider<T>.safeFlatMap(
    crossinline transform: (T) -> Provider<R>,
): Provider<R> = flatMap(object : Transformer<Provider<R>, T> {
    override fun transform(input: T): Provider<R> {
        return transform(input)
    }
})
