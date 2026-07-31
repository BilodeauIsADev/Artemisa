package com.limelight.ui;

import android.view.View;
import android.widget.AbsListView;

public interface AdapterFragmentCallbacks {
    int getAdapterFragmentLayoutId();
    void receiveAbsListView(AbsListView gridView);

    default void receiveAdapterView(View adapterView) {
        receiveAbsListView((AbsListView) adapterView);
    }
}
