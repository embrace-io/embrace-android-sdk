package io.embrace.android.embracesdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @Documented} means that the annotation indicates that elements using this annotation should be documented by JavaDoc.
 * <p>
 * {@code @Target} specifies where we can use the annotation.
 * If you do not define any Target type that means annotation can be applied to any element.
 * <p>
 * {@code @Inherited} signals that a custom annotation used in a class should be inherited by all of its sub classes.
 * <p>
 * {@code @Retention} indicates how long annotations with the annotated type are to be retained.
 * RetentionPolicy.RUNTIME means the annotation should be available at runtime, for inspection via java reflection.
 */

@Documented
@Target(ElementType.TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface StartupActivity {
}
