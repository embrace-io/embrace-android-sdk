package io.embrace.test.fixtures;

import android.view.View;

/**
 * An anonymous inner class which implements OnClickListener.
 */
public class AnonInnerClassOnClickListener {
    public void setupListeners() {
        new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        };
    }
}
