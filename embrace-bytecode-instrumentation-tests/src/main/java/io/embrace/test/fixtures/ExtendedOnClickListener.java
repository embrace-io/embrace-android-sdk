package io.embrace.test.fixtures;

import android.util.Log;
import android.view.View;

import org.jetbrains.annotations.NotNull;

/**
 * A class which extends and overrides an OnClickListener.
 */
public class ExtendedOnClickListener extends CustomOnClickListener {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onClick(@NotNull View view) {
        doSomething();
        super.onClick(view);

        if (view.isEnabled()) {
            Log.d("EmbraceTest", "Clicked a button");
        }
    }

    private int doSomething() {
        return 5209 * 209;
    }
}
