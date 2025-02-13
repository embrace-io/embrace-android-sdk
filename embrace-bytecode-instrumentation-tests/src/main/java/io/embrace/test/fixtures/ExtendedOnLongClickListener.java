package io.embrace.test.fixtures;

import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

/**
 * A class which extends and overrides an OnLongClickListener.
 */
public class ExtendedOnLongClickListener extends CustomOnLongClickListener {

    @Override
    public boolean onLongClick(@Nullable View view) {
        if (view.isEnabled()) {
            Log.d("EmbraceTest", "Clicked a button");
        }
        return super.onLongClick(view);
    }

    private int doSomething() {
        return 5209 * 209;
    }
}
