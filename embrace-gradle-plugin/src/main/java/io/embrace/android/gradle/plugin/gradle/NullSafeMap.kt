package io.embrace.android.gradle.plugin.gradle

import org.gradle.api.Transformer
import org.gradle.api.provider.Provider

/**
 * Workaround that uses Java's @Nullable annotation to correct some incorrect nullability
 * annotations in Gradle.
 * <p>
 * See https://github.com/gradle/gradle/issues/12388
 */
fun <O, I : Any> Provider<I>.nullSafeMap(transform: (I) -> O?): Provider<O> {
    return map(
        object : io.embrace.android.gradle.plugin.gradle.NullSafeTransformer<O?, I>() {
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
inline fun <T, R> Provider<T>.safeFlatMap(
    crossinline transform: (T) -> Provider<R>
): Provider<R> = flatMap(object : Transformer<Provider<R>, T> {
    override fun transform(input: T): Provider<R> {
        return transform(input)
    }
})
