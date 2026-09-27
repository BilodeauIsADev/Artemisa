package com.limelight.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller-first option menu: a rounded card that opens beside the element it belongs to,
 * with a green selection bar. Replaces the platform context menu on TV surfaces.
 */
public class ConsoleMenu {
    public interface OnItemSelectedListener {
        void onItemSelected(int itemId);
    }

    private static class Item {
        final int id;
        final String label;
        final int iconRes;
        final boolean destructive;
        final boolean checked;
        final boolean separator;

        Item(int id, String label, int iconRes, boolean destructive, boolean checked, boolean separator) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
            this.destructive = destructive;
            this.checked = checked;
            this.separator = separator;
        }
    }

    private final Activity activity;
    private final String title;
    private final List<Item> items = new ArrayList<>();
    private Dialog dialog;

    public ConsoleMenu(Activity activity, String title) {
        this.activity = activity;
        this.title = title;
    }

    public ConsoleMenu add(int id, String label, int iconRes) {
        items.add(new Item(id, label, iconRes, false, false, false));
        return this;
    }

    public ConsoleMenu addChecked(int id, String label, int iconRes, boolean checked) {
        items.add(new Item(id, label, iconRes, false, checked, false));
        return this;
    }

    public ConsoleMenu addDestructive(int id, String label, int iconRes) {
        items.add(new Item(id, label, iconRes, true, false, false));
        return this;
    }

    public ConsoleMenu addSeparator() {
        if (!items.isEmpty() && !items.get(items.size() - 1).separator) {
            items.add(new Item(0, null, 0, false, false, true));
        }
        return this;
    }

    public boolean isEmpty() {
        for (Item item : items) {
            if (!item.separator) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shows the menu. With an anchor, the card opens to the right of it, growing away from the
     * nearer screen edge; without one it is centred.
     */
    public void show(View anchor, OnItemSelectedListener listener) {
        show(anchor, listener, null);
    }

    public void show(View anchor, OnItemSelectedListener listener, Runnable onDismiss) {
        if (isEmpty() || activity.isFinishing()) {
            if (onDismiss != null) {
                onDismiss.run();
            }
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        View content = inflater.inflate(R.layout.console_menu, null, false);
        TextView titleView = content.findViewById(R.id.consoleMenuTitle);
        LinearLayout itemContainer = content.findViewById(R.id.consoleMenuItems);

        if (!TextUtils.isEmpty(title)) {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
        }

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View firstRow = null;
        for (Item item : items) {
            if (item.separator) {
                inflater.inflate(R.layout.console_menu_separator, itemContainer, true);
                continue;
            }

            View row = inflater.inflate(R.layout.console_menu_item, itemContainer, false);
            ImageView icon = row.findViewById(R.id.consoleMenuItemIcon);
            TextView label = row.findViewById(R.id.consoleMenuItemLabel);
            ImageView check = row.findViewById(R.id.consoleMenuItemCheck);

            label.setText(item.label);
            if (item.iconRes != 0) {
                icon.setImageResource(item.iconRes);
            }
            else {
                icon.setVisibility(View.GONE);
            }
            check.setVisibility(item.checked ? View.VISIBLE : View.GONE);
            if (item.destructive) {
                row.setBackgroundResource(R.drawable.console_menu_row_danger_background);
                label.setTextColor(AppCompatResources.getColorStateList(activity, R.color.dash_on_focus_danger));
                icon.setImageTintList(AppCompatResources.getColorStateList(activity, R.color.dash_on_focus_danger));
            }

            row.setOnClickListener(v -> {
                dismiss();
                listener.onItemSelected(item.id);
            });
            row.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_BUTTON_A) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    v.performClick();
                }
                return true;
            });

            itemContainer.addView(row);
            if (firstRow == null) {
                firstRow = row;
            }
        }

        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        if (onDismiss != null) {
            dialog.setOnDismissListener(d -> onDismiss.run());
        }
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.55f;
            if (anchor != null && anchor.isAttachedToWindow()) {
                int[] location = new int[2];
                anchor.getLocationOnScreen(location);
                int screenHeight = anchor.getRootView().getHeight();
                int gap = Math.round(12 * activity.getResources().getDisplayMetrics().density);
                params.x = location[0] + anchor.getWidth() + gap;
                if (location[1] + anchor.getHeight() / 2 < screenHeight / 2) {
                    // Anchor in the upper half: open downward from its top edge
                    params.gravity = Gravity.TOP | Gravity.START;
                    params.y = location[1];
                }
                else {
                    // Anchor in the lower half: open upward from its bottom edge
                    params.gravity = Gravity.BOTTOM | Gravity.START;
                    params.y = Math.max(gap, screenHeight - (location[1] + anchor.getHeight()));
                }
            }
            else {
                params.gravity = Gravity.CENTER;
            }
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        dialog.show();
        if (firstRow != null) {
            firstRow.requestFocus();
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
    }
}
