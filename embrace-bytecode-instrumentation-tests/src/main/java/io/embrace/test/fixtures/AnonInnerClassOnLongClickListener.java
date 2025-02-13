package io.embrace.test.fixtures;

import android.view.View;

/**
 * An anonymous inner class which implements OnLongClickListener.
 */
public class AnonInnerClassOnLongClickListener {
    public void setupListeners() {
        new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                return false;
            }
        };
    }
}
