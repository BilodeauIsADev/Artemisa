package com.limelight.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.limelight.R;

/**
 * A controller-friendly, single-row shelf backed by a grid adapter. The focused tile grows
 * in place (the row reflows around it) and reports focus so the screen can follow it.
 */
public class HorizontalGameListView extends HorizontalScrollView {
    public interface OnItemClickListener {
        void onItemClick(View view, int position, long id);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position, long id);
    }

    public interface OnItemFocusListener {
        void onItemFocused(View view, int position);
    }

    private final LinearLayout itemContainer;
    private BaseAdapter adapter;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;
    private OnItemFocusListener itemFocusListener;
    private int focusedPosition;
    private int nextFocusDownTarget = View.NO_ID;
    private View trailingView;
    private boolean initialFocusClaimed;

    private int itemWidth;
    private int itemHeight;
    private int focusedItemWidth;
    private int focusedItemHeight;

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
        setClipChildren(false);
        setClipToPadding(false);

        itemWidth = getResources().getDimensionPixelSize(R.dimen.dash_tile_width);
        itemHeight = getResources().getDimensionPixelSize(R.dimen.dash_tile_height);
        focusedItemWidth = getResources().getDimensionPixelSize(R.dimen.dash_tile_focused_width);
        focusedItemHeight = getResources().getDimensionPixelSize(R.dimen.dash_tile_focused_height);

        itemContainer = new LinearLayout(context);
        itemContainer.setOrientation(LinearLayout.HORIZONTAL);
        itemContainer.setGravity(Gravity.TOP);
        itemContainer.setClipChildren(false);
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

    /** Sizes of a resting and a focused tile, in pixels. */
    public void setItemSizes(int width, int height, int focusedWidth, int focusedHeight) {
        itemWidth = width;
        itemHeight = height;
        focusedItemWidth = focusedWidth;
        focusedItemHeight = focusedHeight;
        rebuildItems();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        itemLongClickListener = listener;
    }

    public void setOnItemFocusListener(OnItemFocusListener listener) {
        itemFocusListener = listener;
    }

    /** Where D-pad down goes from any tile. */
    public void setNextFocusDownTarget(int viewId) {
        nextFocusDownTarget = viewId;
        for (int i = 0; i < itemContainer.getChildCount(); i++) {
            itemContainer.getChildAt(i).setNextFocusDownId(viewId);
        }
    }

    /** An extra focusable view (for example "Add computer") placed after the adapter items. */
    public void setTrailingView(View view) {
        trailingView = view;
        rebuildItems();
    }

    public int getFocusedPosition() {
        return focusedPosition;
    }

    /** The frame of the last focused tile, or null when the row is empty. */
    public View getFocusedItemView() {
        if (adapter == null || adapter.getCount() == 0) {
            return null;
        }
        return itemContainer.getChildAt(Math.min(focusedPosition, adapter.getCount() - 1));
    }

    public boolean requestFocusOnCurrent() {
        View view = getFocusedItemView();
        return view != null && view.requestFocus();
    }

    private void rebuildItems() {
        View focusedChild = itemContainer.findFocus();
        boolean hadFocus = focusedChild != null;
        if (focusedChild != null) {
            Object positionTag = focusedChild.getTag(R.id.horizontal_game_position);
            if (positionTag instanceof Integer) {
                focusedPosition = (Integer) positionTag;
            }
        }

        itemContainer.removeAllViews();
        int count = adapter != null ? adapter.getCount() : 0;
        int gap = getResources().getDimensionPixelSize(R.dimen.dash_focus_gap);
        int spacing = dpToPx(5);

        for (int position = 0; position < count; position++) {
            final int itemPosition = position;
            final FrameLayout focusFrame = new FrameLayout(getContext());
            focusFrame.setBackgroundResource(R.drawable.console_grid_selector);
            focusFrame.setPadding(gap, gap, gap, gap);
            focusFrame.setFocusable(true);
            focusFrame.setClickable(true);
            focusFrame.setClipChildren(false);
            focusFrame.setTag(R.id.horizontal_game_position, position);
            focusFrame.setContentDescription(String.valueOf(adapter.getItem(position)));
            if (nextFocusDownTarget != View.NO_ID) {
                focusFrame.setNextFocusDownId(nextFocusDownTarget);
            }
            if (position == Math.min(focusedPosition, count - 1) &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // The first key press lands on the current tile, not the top bar
                focusFrame.setFocusedByDefault(true);
            }

            final View itemView = adapter.getView(position, null, itemContainer);
            focusFrame.addView(itemView, new FrameLayout.LayoutParams(itemWidth, itemHeight));

            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            frameParams.setMarginEnd(spacing);
            itemContainer.addView(focusFrame, frameParams);

            focusFrame.setOnFocusChangeListener((view, hasFocus) -> {
                animateSize(itemView, hasFocus);
                if (hasFocus) {
                    focusedPosition = itemPosition;
                    if (itemFocusListener != null) {
                        itemFocusListener.onItemFocused(view, itemPosition);
                    }
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

        if (trailingView != null) {
            if (trailingView.getParent() instanceof ViewGroup) {
                ((ViewGroup) trailingView.getParent()).removeView(trailingView);
            }
            itemContainer.addView(trailingView);
        }

        if (count > 0) {
            int positionToRestore = Math.min(focusedPosition, count - 1);
            View restore = itemContainer.getChildAt(positionToRestore);
            // The row takes focus the first time it fills, then keeps whatever the user chose
            if (hadFocus || !isAnyViewFocused() || !initialFocusClaimed) {
                initialFocusClaimed = true;
                restore.post(restore::requestFocus);
            }
        }
    }

    private boolean isAnyViewFocused() {
        View root = getRootView();
        return root != null && root.findFocus() != null && root.findFocus() != this;
    }

    private void animateSize(View itemView, boolean focused) {
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        final int startWidth = params.width;
        final int startHeight = params.height;
        final int endWidth = focused ? focusedItemWidth : itemWidth;
        final int endHeight = focused ? focusedItemHeight : itemHeight;
        if (startWidth == endWidth && startHeight == endHeight) {
            return;
        }

        Object running = itemView.getTag(R.id.horizontal_game_animator);
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(160);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            ViewGroup.LayoutParams p = itemView.getLayoutParams();
            p.width = Math.round(startWidth + (endWidth - startWidth) * t);
            p.height = Math.round(startHeight + (endHeight - startHeight) * t);
            itemView.setLayoutParams(p);
        });
        itemView.setTag(R.id.horizontal_game_animator, animator);
        animator.start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
