package io.embrace.android.gradle.plugin.gradle;

import org.gradle.api.Transformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Workaround that uses Java's @Nullable annotation to correct some incorrect nullability
 * annotations in Gradle.
 * <p>
 * See https://github.com/gradle/gradle/issues/12388
 */
public abstract class NullSafeTransformer<O, I> implements Transformer<O, I> {
    @Override
    @Nullable
    public abstract O transform(@NotNull I input);
}
