package io.embrace.test.fixtures;

import android.view.View;

/**
 * An object which has onLongClick methods but that does not implement OnLongClickListener.
 */
public class MissingInterfaceOnLongClickListener {
    public boolean onLongClick(View view) {
        return true;
    }

    public boolean onLongClick() {
        return true;
    }
}
