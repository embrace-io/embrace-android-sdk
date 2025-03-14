package io.embrace.test.fixtures;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * A custom object which implements OnClickListener via a lambda.
 */
public class JavaLambdaOnClickListener extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            view.setOnClickListener(lambdaView -> {
                Log.d("Embrace", "test");
            });
        }
        return view;
    }
}
