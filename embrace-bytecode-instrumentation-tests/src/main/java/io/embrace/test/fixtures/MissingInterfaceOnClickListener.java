package io.embrace.test.fixtures;

import android.view.View;

/**
 * An object which has onClick methods but that does not implement OnClickListener.
 */
public class MissingInterfaceOnClickListener {
    public void onClick(View view) {

    }

    public void onClick() {
    }
}
