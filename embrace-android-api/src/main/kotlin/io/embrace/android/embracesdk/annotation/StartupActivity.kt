package io.embrace.android.embracesdk.annotation

import java.lang.annotation.Inherited

/**
 * `@Documented` means that the annotation indicates that elements using this annotation should be documented by JavaDoc.
 *
 *
 * `@Target` specifies where we can use the annotation.
 * If you do not define any Target type that means annotation can be applied to any element.
 *
 *
 * `@Inherited` signals that a custom annotation used in a class should be inherited by all of its sub classes.
 *
 *
 * `@Retention` indicates how long annotations with the annotated type are to be retained.
 * RetentionPolicy.RUNTIME means the annotation should be available at runtime, for inspection via java reflection.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Inherited
@Retention(AnnotationRetention.RUNTIME)
public annotation class StartupActivity
