package com.limelight.ui;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.limelight.R;

import java.util.ArrayList;

/**
 * A controller-driven cover carousel backed by a grid adapter. The focused item sits full size
 * in the centre; its neighbours shrink and fade towards the edges, and left/right slide the row.
 */
public class SpotlightCarouselView extends FrameLayout {
    public interface OnItemClickListener {
        void onItemClick(View view, int position, long id);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position, long id);
    }

    public interface OnItemFocusListener {
        void onItemFocused(View view, int position);
    }

    // Scale and opacity by distance from the focused item; three or more away is hidden
    private static final float[] SCALES = {1f, 0.75f, 0.625f, 0.55f};
    private static final float[] ALPHAS = {1f, 0.7f, 0.35f, 0f};
    private static final int SLIDE_MS = 220;
    private static final Interpolator SLIDE_INTERPOLATOR = new DecelerateInterpolator(2f);

    private BaseAdapter adapter;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;
    private OnItemFocusListener itemFocusListener;
    private int focusedPosition;
    private int nextFocusDownTarget = View.NO_ID;
    private View trailingView;
    private boolean initialFocusClaimed;
    // False until the items have been placed once after a rebuild; that placement is not animated
    private boolean placed;

    private int itemWidth;
    private int itemHeight;
    private final int spacing;

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

    public SpotlightCarouselView(Context context) {
        this(context, null);
    }

    public SpotlightCarouselView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpotlightCarouselView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);

        itemWidth = getResources().getDimensionPixelSize(R.dimen.dash_spotlight_width);
        itemHeight = getResources().getDimensionPixelSize(R.dimen.dash_spotlight_height);
        spacing = getResources().getDimensionPixelSize(R.dimen.dash_spotlight_spacing);
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

    /** Size of the focused item, in pixels. Neighbours are scaled down from it. */
    public void setItemSize(int width, int height) {
        itemWidth = width;
        itemHeight = height;
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

    /** Where D-pad down goes from any item. */
    public void setNextFocusDownTarget(int viewId) {
        nextFocusDownTarget = viewId;
        for (int i = 0; i < getAdapterCount(); i++) {
            getChildAt(i).setNextFocusDownId(viewId);
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

    /** The frame of the last focused adapter item, or null when there are none. */
    public View getFocusedItemView() {
        int count = getAdapterCount();
        if (count == 0) {
            return null;
        }
        return getChildAt(Math.min(focusedPosition, count - 1));
    }

    public boolean requestFocusOnCurrent() {
        View view = getChildAt(Math.min(focusedPosition, getChildCount() - 1));
        return view != null && view.requestFocus();
    }

    private int getAdapterCount() {
        return adapter != null ? adapter.getCount() : 0;
    }

    private void rebuildItems() {
        boolean hadFocus = hasFocus();

        removeAllViews();
        int count = getAdapterCount();
        int gap = getResources().getDimensionPixelSize(R.dimen.dash_focus_gap);

        for (int position = 0; position < count; position++) {
            final int itemPosition = position;
            final FrameLayout focusFrame = new FrameLayout(getContext());
            focusFrame.setBackgroundResource(R.drawable.console_grid_selector);
            focusFrame.setPadding(gap, gap, gap, gap);
            focusFrame.setFocusable(true);
            focusFrame.setClickable(true);
            focusFrame.setClipChildren(false);
            focusFrame.setContentDescription(String.valueOf(adapter.getItem(position)));
            if (nextFocusDownTarget != View.NO_ID) {
                focusFrame.setNextFocusDownId(nextFocusDownTarget);
            }
            if (position == Math.min(focusedPosition, count - 1) &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // The first key press lands on the current item, not the top bar
                focusFrame.setFocusedByDefault(true);
            }

            View itemView = adapter.getView(position, null, this);
            focusFrame.addView(itemView, new LayoutParams(itemWidth, itemHeight));
            addView(focusFrame, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

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
            addView(trailingView, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }

        focusedPosition = Math.max(0, Math.min(focusedPosition, getChildCount() - 1));
        placed = false;

        if (count > 0) {
            View restore = getChildAt(Math.min(focusedPosition, count - 1));
            // The carousel takes focus the first time it fills, then keeps whatever the user chose
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!placed && getChildCount() > 0) {
            placed = true;
            placeItems(false);
        }
    }

    // Every item is laid out in the centre; translation spreads them out from the focused one.
    private void placeItems(boolean animate) {
        int count = getChildCount();
        if (count == 0) {
            return;
        }

        float[] offsets = new float[count];
        for (int i = focusedPosition + 1; i < count; i++) {
            offsets[i] = offsets[i - 1] + scaledWidth(i - 1) / 2 + spacing + scaledWidth(i) / 2;
        }
        for (int i = focusedPosition - 1; i >= 0; i--) {
            offsets[i] = offsets[i + 1] - scaledWidth(i + 1) / 2 - spacing - scaledWidth(i) / 2;
        }

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int distance = Math.min(Math.abs(i - focusedPosition), SCALES.length - 1);
            float scale = SCALES[distance];
            float alpha = ALPHAS[distance];

            // Nearer items draw over farther ones
            child.setTranslationZ(-distance);
            child.animate().cancel();
            if (animate) {
                child.animate()
                        .translationX(offsets[i])
                        .scaleX(scale)
                        .scaleY(scale)
                        .alpha(alpha)
                        .setDuration(SLIDE_MS)
                        .setInterpolator(SLIDE_INTERPOLATOR)
                        .start();
            } else {
                child.setTranslationX(offsets[i]);
                child.setScaleX(scale);
                child.setScaleY(scale);
                child.setAlpha(alpha);
            }
        }
    }

    private float scaledWidth(int index) {
        int distance = Math.min(Math.abs(index - focusedPosition), SCALES.length - 1);
        return getChildAt(index).getWidth() * SCALES[distance];
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);

        int index = indexOfChild(child);
        if (index < 0) {
            return;
        }
        if (index != focusedPosition) {
            focusedPosition = index;
            if (placed) {
                placeItems(true);
            }
        }
        if (index < getAdapterCount() && itemFocusListener != null) {
            itemFocusListener.onItemFocused(child, index);
        }
    }

    // Only the current item is reachable by focus search; the others overlap it on screen.
    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View current = getChildAt(Math.min(focusedPosition, getChildCount() - 1));
        if (current != null) {
            current.addFocusables(views, direction, focusableMode);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && hasFocus()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveFocus(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveFocus(1);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void moveFocus(int delta) {
        int target = focusedPosition + delta;
        if (target < 0 || target >= getChildCount()) {
            return;
        }
        if (getChildAt(target).requestFocus()) {
            playSoundEffect(delta < 0 ?
                    SoundEffectConstants.NAVIGATION_LEFT : SoundEffectConstants.NAVIGATION_RIGHT);
        }
    }
}
