package io.embrace.test.fixtures;

/**
 * Superclass for constructor instrumentation fixtures, whose constructor takes an argument so that
 * subclasses build objects before calling super(...).
 */
@SuppressWarnings("UnknownNullness")
public class ConstructorTargetBase {

    private final Object value;

    public ConstructorTargetBase(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
