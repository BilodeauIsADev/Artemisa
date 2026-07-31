package com.limelight.ui;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.limelight.R;

/**
 * A controller-friendly, single-row shelf backed by the same adapter as the game grid.
 */
public class HorizontalGameListView extends HorizontalScrollView {
    public interface OnItemClickListener {
        void onItemClick(View view, int position, long id);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position, long id);
    }

    private final LinearLayout itemContainer;
    private BaseAdapter adapter;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;
    private int focusedPosition;

    private final DataSetObserver dataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            rebuildItems();
        }

        @Override
        public void onInvalidated() {
            rebuildItems();
        }
    };

    public HorizontalGameListView(Context context) {
        this(context, null);
    }

    public HorizontalGameListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalGameListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setSmoothScrollingEnabled(true);
        setHorizontalFadingEdgeEnabled(false);
        setFocusable(false);

        itemContainer = new LinearLayout(context);
        itemContainer.setOrientation(LinearLayout.HORIZONTAL);
        itemContainer.setGravity(Gravity.CENTER_VERTICAL);
        addView(itemContainer, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setAdapter(BaseAdapter adapter) {
        if (this.adapter != null) {
            this.adapter.unregisterDataSetObserver(dataSetObserver);
        }

        this.adapter = adapter;
        if (adapter != null) {
            adapter.registerDataSetObserver(dataSetObserver);
        }
        rebuildItems();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        itemLongClickListener = listener;
    }

    private void rebuildItems() {
        View focusedChild = itemContainer.findFocus();
        if (focusedChild != null) {
            Object positionTag = focusedChild.getTag(R.id.horizontal_game_position);
            if (positionTag instanceof Integer) {
                focusedPosition = (Integer) positionTag;
            }
        }

        itemContainer.removeAllViews();
        if (adapter == null) {
            return;
        }

        for (int position = 0; position < adapter.getCount(); position++) {
            final int itemPosition = position;
            final FrameLayout focusFrame = new FrameLayout(getContext());
            focusFrame.setBackgroundResource(R.drawable.console_grid_selector);
            focusFrame.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            focusFrame.setFocusable(true);
            focusFrame.setClickable(true);
            focusFrame.setTag(R.id.horizontal_game_position, position);
            focusFrame.setContentDescription(String.valueOf(adapter.getItem(position)));

            View itemView = adapter.getView(position, null, itemContainer);
            focusFrame.addView(itemView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            frameParams.setMarginEnd(dpToPx(18));
            itemContainer.addView(focusFrame, frameParams);

            focusFrame.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    focusedPosition = itemPosition;
                }
            });
            focusFrame.setOnClickListener(view -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(view, itemPosition, adapter.getItemId(itemPosition));
                }
            });
            focusFrame.setOnLongClickListener(view -> itemLongClickListener != null &&
                    itemLongClickListener.onItemLongClick(
                            view, itemPosition, adapter.getItemId(itemPosition)));
        }

        if (itemContainer.getChildCount() > 0) {
            int positionToRestore = Math.min(focusedPosition, itemContainer.getChildCount() - 1);
            itemContainer.getChildAt(positionToRestore).post(
                    () -> itemContainer.getChildAt(positionToRestore).requestFocus());
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
