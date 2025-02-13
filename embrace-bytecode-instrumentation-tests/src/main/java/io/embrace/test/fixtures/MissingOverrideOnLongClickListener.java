package io.embrace.test.fixtures;

import android.view.View;

/**
 * An object which implements OnLongClickListener but forgot the override annotation.
 */
public class MissingOverrideOnLongClickListener implements View.OnLongClickListener {
    public boolean onLongClick(View view) {
        return false;
    }
}
