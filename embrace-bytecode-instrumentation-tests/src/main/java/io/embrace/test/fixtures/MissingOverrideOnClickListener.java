package io.embrace.test.fixtures;

import android.view.View;

/**
 * An object which implements OnClickListener but forgot the override annotation.
 */
public class MissingOverrideOnClickListener implements View.OnClickListener {
    public void onClick(View view) {

    }
}
