package com.limelight.grid;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_dash_monitor);
        txtView.setText(obj.details.name);
        parentView.setContentDescription(obj.details.name);

        boolean online = obj.details.state == ComputerDetails.State.ONLINE;
        imgView.setAlpha(online ? 1.0f : 0.45f);
        prgView.setVisibility(obj.details.state == ComputerDetails.State.UNKNOWN ? View.VISIBLE : View.GONE);

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_dash_moon);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (online && obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_dash_lock);
            overlayView.setVisibility(View.VISIBLE);
        }
        else if (online && obj.details.runningGameId != 0) {
            overlayView.setImageResource(R.drawable.ic_dash_play);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }
    }
}
