package io.embrace.test.fixtures;

import android.view.View;

/**
 * Contains nested classes which implement OnClickListener.
 */
public class JavaNested {

    @SuppressWarnings("InnerClassMayBeStatic")
    public class JavaInnerListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {

        }
    }

    public static class JavaStaticListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {

        }
    }
}
