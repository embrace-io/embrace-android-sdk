package io.embrace.test.fixtures;

import java.util.ArrayList;

@SuppressWarnings({"unused", "UnknownNullness"})
public class JavaConstructorTarget extends ConstructorTargetBase {

    private String state;

    public JavaConstructorTarget() {
        this(new Object());
    }

    public JavaConstructorTarget(Object value) {
        super(value);
    }

    public JavaConstructorTarget(boolean flag) {
        super(flag ? new StringBuilder() : new ArrayList<>());
    }

    public JavaConstructorTarget(int depth) {
        this(depth > 0 ? new JavaConstructorTarget(depth - 1) : null);
    }

    public JavaConstructorTarget(String value) {
        super(value);
        if (value.isEmpty()) {
            return;
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return;
        }
        System.out.println(value);
    }

    public JavaConstructorTarget(long value) {
        super(requirePositive(value));
    }

    public JavaConstructorTarget(char value) {
        super(value);
        state = "initialized";
        if (value == 'x') {
            throw new IllegalArgumentException("value must not be x");
        }
    }

    @Override
    public String toString() {
        return String.valueOf(state);
    }

    private static Object requirePositive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        return value;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class Inner extends ConstructorTargetBase {

        public Inner() {
            super(new Object());
        }
    }
}
