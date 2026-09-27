package com.limelight.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * D-pad and analog-stick on-screen keyboard for TV/gamepad streaming.
 */
public class GamepadOnscreenKeyboard {

    private static final int PAGE_LETTERS = 0;
    private static final int PAGE_SYMBOLS = 1;
    private static final int PAGE_CONTROLS = 2;
    private static final int PAGE_COUNT = 3;

    private static final float STICK_THRESHOLD = 0.62f;
    private static final float STICK_RESET = 0.35f;
    private static final long STICK_NAV_COOLDOWN_MS = 165;
    private static final long REPEAT_START_MS = 400;
    private static final long REPEAT_INTERVAL_MS = 75;

    private final Game game;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Dialog dialog;
    private LinearLayout rowsContainer;
    private TextView pageLabel;
    private TextView[] pageSegments;

    private int page = PAGE_LETTERS;
    private boolean shiftHeld;
    private boolean shiftUsed;
    private boolean capsLock;
    private boolean ctrlHeld;
    private boolean ctrlUsed;
    private boolean altHeld;
    private boolean altUsed;
    private boolean winHeld;
    private boolean winUsed;

    private long lastStickNavTime;
    private boolean stickCentered = true;
    private KeyView repeatingKey;
    private final Runnable yRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendChar(KeyEvent.KEYCODE_DEL, false, false);
            handler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };
    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (repeatingKey != null) {
                repeatingKey.fire();
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        }
    };

    private int keyRowHeightPx;

    public GamepadOnscreenKeyboard(Game game) {
        this.game = game;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        resetState();
        createDialog();
        bindPage(true);
        dialog.show();
        configureWindow();
    }

    public void dismiss() {
        handler.removeCallbacks(repeatRunnable);
        handler.removeCallbacks(yRepeatRunnable);
        repeatingKey = null;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
        rowsContainer = null;
        pageLabel = null;
    }

    private void resetState() {
        page = PAGE_LETTERS;
        shiftHeld = false;
        shiftUsed = false;
        capsLock = false;
        ctrlHeld = false;
        ctrlUsed = false;
        altHeld = false;
        altUsed = false;
        winHeld = false;
        winUsed = false;
        lastStickNavTime = 0;
        stickCentered = true;
    }

    private void createDialog() {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(game, themeResId);
        View contentView = LayoutInflater.from(themedContext)
                .inflate(R.layout.dialog_gamepad_keyboard, new FrameLayout(themedContext), false);
        View sheet = contentView.findViewById(R.id.gamepadKeyboardSheet);
        if (sheet.getBackground() != null) {
            int opacity = PreferenceConfiguration.readPreferences(game).quickMenuOpacity;
            sheet.getBackground().mutate().setAlpha(Math.round(opacity * 2.55f));
        }

        rowsContainer = contentView.findViewById(R.id.gamepadKeyboardRows);
        pageLabel = contentView.findViewById(R.id.gamepadKeyboardPage);
        pageSegments = new TextView[] {
                contentView.findViewById(R.id.gamepadKeyboardSegLetters),
                contentView.findViewById(R.id.gamepadKeyboardSegSymbols),
                contentView.findViewById(R.id.gamepadKeyboardSegControls)
        };
        keyRowHeightPx = computeKeyRowHeight();

        dialog = new Dialog(themedContext);
        dialog.setContentView(contentView);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((ignored, keyCode, event) -> handleDialogKey(keyCode, event));
        dialog.setOnDismissListener(ignored -> {
            handler.removeCallbacks(repeatRunnable);
            handler.removeCallbacks(yRepeatRunnable);
            repeatingKey = null;
            if (dialog != null) {
                dialog = null;
                rowsContainer = null;
                pageLabel = null;
            }
        });
    }

    private void configureWindow() {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.34f;
        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setOnGenericMotionListener((v, event) -> {
            int source = event.getSource();
            if ((source & InputDevice.SOURCE_JOYSTICK) != 0 ||
                    (source & InputDevice.SOURCE_GAMEPAD) != 0) {
                handleStickNavigation(event);
                return true;
            }
            return false;
        });
    }

    private boolean handleDialogKey(int keyCode, KeyEvent event) {
        if (isConfirmKey(keyCode)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && event.getRepeatCount() == 0) {
                sendChar(KeyEvent.KEYCODE_DEL, false, false);
                handler.postDelayed(yRepeatRunnable, REPEAT_START_MS);
                return true;
            }
            return isBackKey(keyCode) || isShortcutKey(keyCode);
        }
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        if (isBackKey(keyCode)) {
            dismiss();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            sendChar(KeyEvent.KEYCODE_SPACE, false, false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            handler.removeCallbacks(yRepeatRunnable);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            setPage(page - 1, true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            setPage(page + 1, true);
            return true;
        }
        return false;
    }

    private boolean handleStickNavigation(MotionEvent event) {
        int source = event.getSource();
        if ((source & InputDevice.SOURCE_JOYSTICK) == 0 &&
                (source & InputDevice.SOURCE_GAMEPAD) == 0) {
            return false;
        }

        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < 0.01f && Math.abs(y) < 0.01f) {
            x = event.getAxisValue(MotionEvent.AXIS_X);
            y = event.getAxisValue(MotionEvent.AXIS_Y);
        }

        if (Math.abs(x) < STICK_RESET && Math.abs(y) < STICK_RESET) {
            stickCentered = true;
            return true;
        }

        int direction = 0;
        if (Math.abs(x) >= Math.abs(y) && Math.abs(x) >= STICK_THRESHOLD) {
            direction = x > 0 ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
        } else if (Math.abs(y) >= STICK_THRESHOLD) {
            direction = y > 0 ? View.FOCUS_DOWN : View.FOCUS_UP;
        }
        if (direction == 0) {
            return true;
        }

        long now = SystemClock.uptimeMillis();
        if (!stickCentered && now - lastStickNavTime < STICK_NAV_COOLDOWN_MS) {
            return true;
        }
        stickCentered = false;
        lastStickNavTime = now;

        View focused = dialog != null ? dialog.getCurrentFocus() : null;
        if (focused != null) {
            View next = focused.focusSearch(direction);
            if (next != null) {
                next.requestFocus();
            }
        }
        return true;
    }

    private void setPage(int nextPage, boolean requestFocus) {
        page = ((nextPage % PAGE_COUNT) + PAGE_COUNT) % PAGE_COUNT;
        bindPage(requestFocus);
    }

    private void bindPage(boolean requestFocus) {
        if (rowsContainer == null || pageLabel == null) {
            return;
        }
        pageLabel.setText(pageTitle());
        updatePageSegments();
        rowsContainer.removeAllViews();

        KeyDef[][] layout = currentLayout();
        List<List<KeyView>> grid = new ArrayList<>();
        KeyView focusTarget = null;

        for (KeyDef[] rowDefs : layout) {
            LinearLayout row = new LinearLayout(rowsContainer.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    keyRowHeightPx));
            if (!grid.isEmpty()) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = dp(5);
            }

            List<KeyView> rowViews = new ArrayList<>();
            for (int i = 0; i < rowDefs.length; i++) {
                KeyView key = new KeyView(rowDefs[i]);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, rowDefs[i].weight);
                if (i > 0) {
                    params.setMarginStart(dp(5));
                }
                row.addView(key.view, params);
                rowViews.add(key);
                if (focusTarget == null && rowDefs[i].action == Action.CHAR &&
                        rowDefs[i].keyCode == KeyEvent.KEYCODE_G) {
                    focusTarget = key;
                }
                if (focusTarget == null && rowDefs[i].action == Action.CHAR) {
                    focusTarget = key;
                }
            }
            rowsContainer.addView(row);
            grid.add(rowViews);
        }

        wrapRowFocus(grid);
        refreshKeyAppearance();

        KeyView target = focusTarget;
        if (requestFocus && target != null) {
            target.view.post(target.view::requestFocus);
        }
    }

    private void wrapRowFocus(List<List<KeyView>> grid) {
        for (List<KeyView> row : grid) {
            if (row.isEmpty()) {
                continue;
            }
            KeyView first = row.get(0);
            KeyView last = row.get(row.size() - 1);
            first.view.setNextFocusLeftId(last.view.getId());
            last.view.setNextFocusRightId(first.view.getId());
        }
        if (grid.size() < 2) {
            return;
        }
        List<KeyView> top = grid.get(0);
        List<KeyView> bottom = grid.get(grid.size() - 1);
        for (int i = 0; i < top.size(); i++) {
            KeyView dest = bottom.get(Math.min(i, bottom.size() - 1));
            top.get(i).view.setNextFocusUpId(dest.view.getId());
        }
        for (int i = 0; i < bottom.size(); i++) {
            KeyView dest = top.get(Math.min(i, top.size() - 1));
            bottom.get(i).view.setNextFocusDownId(dest.view.getId());
        }
    }

    // The segmented control shows which page LB/RB has selected.
    private void updatePageSegments() {
        if (pageSegments == null) {
            return;
        }
        int selected = page == PAGE_SYMBOLS ? 1 : page == PAGE_CONTROLS ? 2 : 0;
        for (int i = 0; i < pageSegments.length; i++) {
            boolean on = i == selected;
            pageSegments[i].setBackgroundResource(on ? R.drawable.dash_segment_on : 0);
            pageSegments[i].setTextColor(game.getResources().getColor(on ? R.color.dash_ink : R.color.dash_text_2));
        }
    }

    private String pageTitle() {
        switch (page) {
            case PAGE_SYMBOLS:
                return game.getString(R.string.console_keyboard_page_symbols);
            case PAGE_CONTROLS:
                return game.getString(R.string.console_keyboard_page_controls);
            default:
                return game.getString(R.string.console_keyboard_page_letters);
        }
    }

    private KeyDef[][] currentLayout() {
        switch (page) {
            case PAGE_SYMBOLS:
                return symbolsPage();
            case PAGE_CONTROLS:
                return controlsPage();
            default:
                return lettersPage();
        }
    }

    private KeyDef[][] lettersPage() {
        return new KeyDef[][] {
                new KeyDef[] {
                        ch("1", "!", KeyEvent.KEYCODE_1),
                        ch("2", "@", KeyEvent.KEYCODE_2),
                        ch("3", "#", KeyEvent.KEYCODE_3),
                        ch("4", "$", KeyEvent.KEYCODE_4),
                        ch("5", "%", KeyEvent.KEYCODE_5),
                        ch("6", "^", KeyEvent.KEYCODE_6),
                        ch("7", "&", KeyEvent.KEYCODE_7),
                        ch("8", "*", KeyEvent.KEYCODE_8),
                        ch("9", "(", KeyEvent.KEYCODE_9),
                        ch("0", ")", KeyEvent.KEYCODE_0)
                },
                new KeyDef[] {
                        letter("q", KeyEvent.KEYCODE_Q),
                        letter("w", KeyEvent.KEYCODE_W),
                        letter("e", KeyEvent.KEYCODE_E),
                        letter("r", KeyEvent.KEYCODE_R),
                        letter("t", KeyEvent.KEYCODE_T),
                        letter("y", KeyEvent.KEYCODE_Y),
                        letter("u", KeyEvent.KEYCODE_U),
                        letter("i", KeyEvent.KEYCODE_I),
                        letter("o", KeyEvent.KEYCODE_O),
                        letter("p", KeyEvent.KEYCODE_P)
                },
                new KeyDef[] {
                        letter("a", KeyEvent.KEYCODE_A),
                        letter("s", KeyEvent.KEYCODE_S),
                        letter("d", KeyEvent.KEYCODE_D),
                        letter("f", KeyEvent.KEYCODE_F),
                        letter("g", KeyEvent.KEYCODE_G),
                        letter("h", KeyEvent.KEYCODE_H),
                        letter("j", KeyEvent.KEYCODE_J),
                        letter("k", KeyEvent.KEYCODE_K),
                        letter("l", KeyEvent.KEYCODE_L),
                        ch("'", "\"", KeyEvent.KEYCODE_APOSTROPHE)
                },
                new KeyDef[] {
                        letter("z", KeyEvent.KEYCODE_Z),
                        letter("x", KeyEvent.KEYCODE_X),
                        letter("c", KeyEvent.KEYCODE_C),
                        letter("v", KeyEvent.KEYCODE_V),
                        letter("b", KeyEvent.KEYCODE_B),
                        letter("n", KeyEvent.KEYCODE_N),
                        letter("m", KeyEvent.KEYCODE_M),
                        ch(",", "<", KeyEvent.KEYCODE_COMMA),
                        ch(".", ">", KeyEvent.KEYCODE_PERIOD),
                        ch("/", "?", KeyEvent.KEYCODE_SLASH)
                },
                actionRow()
        };
    }

    private KeyDef[][] symbolsPage() {
        return new KeyDef[][] {
                new KeyDef[] {
                        ch("-", KeyEvent.KEYCODE_MINUS),
                        shifted("_", KeyEvent.KEYCODE_MINUS),
                        ch("=", KeyEvent.KEYCODE_EQUALS),
                        shifted("+", KeyEvent.KEYCODE_EQUALS),
                        ch("[", KeyEvent.KEYCODE_LEFT_BRACKET),
                        ch("]", KeyEvent.KEYCODE_RIGHT_BRACKET),
                        shifted("{", KeyEvent.KEYCODE_LEFT_BRACKET),
                        shifted("}", KeyEvent.KEYCODE_RIGHT_BRACKET),
                        ch("\\", KeyEvent.KEYCODE_BACKSLASH),
                        shifted("|", KeyEvent.KEYCODE_BACKSLASH)
                },
                new KeyDef[] {
                        ch(";", KeyEvent.KEYCODE_SEMICOLON),
                        shifted(":", KeyEvent.KEYCODE_SEMICOLON),
                        ch("'", KeyEvent.KEYCODE_APOSTROPHE),
                        shifted("\"", KeyEvent.KEYCODE_APOSTROPHE),
                        ch("`", KeyEvent.KEYCODE_GRAVE),
                        shifted("~", KeyEvent.KEYCODE_GRAVE),
                        shifted("!", KeyEvent.KEYCODE_1),
                        shifted("@", KeyEvent.KEYCODE_2),
                        shifted("#", KeyEvent.KEYCODE_3),
                        shifted("$", KeyEvent.KEYCODE_4)
                },
                new KeyDef[] {
                        shifted("%", KeyEvent.KEYCODE_5),
                        shifted("^", KeyEvent.KEYCODE_6),
                        shifted("&", KeyEvent.KEYCODE_7),
                        shifted("*", KeyEvent.KEYCODE_8),
                        shifted("(", KeyEvent.KEYCODE_9),
                        shifted(")", KeyEvent.KEYCODE_0),
                        shifted("<", KeyEvent.KEYCODE_COMMA),
                        shifted(">", KeyEvent.KEYCODE_PERIOD),
                        ch(",", KeyEvent.KEYCODE_COMMA),
                        ch(".", KeyEvent.KEYCODE_PERIOD)
                },
                new KeyDef[] {
                        action(R.string.console_keyboard_key_tab, Action.TAB, 1),
                        action(R.string.console_keyboard_key_esc, Action.ESC, 1),
                        action(R.string.console_keyboard_key_ins, Action.INSERT, 1),
                        action(R.string.console_keyboard_key_del, Action.DELETE, 1),
                        action(R.string.console_keyboard_key_home, Action.HOME, 1),
                        action(R.string.console_keyboard_key_end, Action.END, 1)
                },
                actionRow()
        };
    }

    private KeyDef[][] controlsPage() {
        return new KeyDef[][] {
                new KeyDef[] {
                        ch("F1", KeyEvent.KEYCODE_F1),
                        ch("F2", KeyEvent.KEYCODE_F2),
                        ch("F3", KeyEvent.KEYCODE_F3),
                        ch("F4", KeyEvent.KEYCODE_F4),
                        ch("F5", KeyEvent.KEYCODE_F5),
                        ch("F6", KeyEvent.KEYCODE_F6)
                },
                new KeyDef[] {
                        ch("F7", KeyEvent.KEYCODE_F7),
                        ch("F8", KeyEvent.KEYCODE_F8),
                        ch("F9", KeyEvent.KEYCODE_F9),
                        ch("F10", KeyEvent.KEYCODE_F10),
                        ch("F11", KeyEvent.KEYCODE_F11),
                        ch("F12", KeyEvent.KEYCODE_F12)
                },
                new KeyDef[] {
                        action(R.string.console_keyboard_key_esc, Action.ESC, 1),
                        action(R.string.console_keyboard_key_tab, Action.TAB, 1),
                        action(R.string.console_keyboard_key_caps, Action.CAPS, 1),
                        action(R.string.console_keyboard_key_ins, Action.INSERT, 1),
                        action(R.string.console_keyboard_key_del, Action.DELETE, 1),
                        action(R.string.console_keyboard_key_prtsc, Action.PRINT, 1),
                        action(R.string.console_keyboard_key_pause, Action.PAUSE, 1)
                },
                new KeyDef[] {
                        ch("←", KeyEvent.KEYCODE_DPAD_LEFT),
                        ch("↑", KeyEvent.KEYCODE_DPAD_UP),
                        ch("↓", KeyEvent.KEYCODE_DPAD_DOWN),
                        ch("→", KeyEvent.KEYCODE_DPAD_RIGHT),
                        action(R.string.console_keyboard_key_pgup, Action.PAGE_UP, 1),
                        action(R.string.console_keyboard_key_pgdn, Action.PAGE_DOWN, 1),
                        action(R.string.console_keyboard_key_home, Action.HOME, 1),
                        action(R.string.console_keyboard_key_end, Action.END, 1)
                },
                actionRow()
        };
    }

    private KeyDef[] actionRow() {
        int pageLabelRes = R.string.console_keyboard_key_letters;
        Action nextPage = Action.PAGE_LETTERS;
        if (page == PAGE_LETTERS) {
            pageLabelRes = R.string.console_keyboard_key_symbols;
            nextPage = Action.PAGE_SYMBOLS;
        } else if (page == PAGE_SYMBOLS) {
            pageLabelRes = R.string.console_keyboard_key_controls;
            nextPage = Action.PAGE_CONTROLS;
        }
        return new KeyDef[] {
                action(R.string.console_keyboard_key_shift, Action.SHIFT, 1.3f),
                action(R.string.console_keyboard_key_ctrl, Action.CTRL, 1),
                action(R.string.console_keyboard_key_alt, Action.ALT, 1),
                action(R.string.console_keyboard_key_win, Action.WIN, 1),
                action(pageLabelRes, nextPage, 1.2f),
                action(R.string.console_keyboard_key_space, Action.SPACE, 3.2f),
                action(R.string.console_keyboard_key_backspace, Action.BACKSPACE, 1.4f),
                action(R.string.console_keyboard_key_enter, Action.ENTER, 1.5f),
                action(R.string.console_keyboard_key_done, Action.CLOSE, 1.3f)
        };
    }

    private void refreshKeyAppearance() {
        if (rowsContainer == null) {
            return;
        }
        for (int r = 0; r < rowsContainer.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) rowsContainer.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View child = row.getChildAt(c);
                Object tag = child.getTag();
                if (tag instanceof KeyView) {
                    ((KeyView) tag).refresh();
                }
            }
        }
    }

    private void toggleModifier(Action action) {
        switch (action) {
            case SHIFT:
                if (shiftHeld) {
                    shiftHeld = false;
                    if (!shiftUsed) {
                        sendSolo((short) KeyboardTranslator.VK_LSHIFT);
                    }
                    shiftUsed = false;
                } else {
                    shiftHeld = true;
                    shiftUsed = false;
                }
                break;
            case CTRL:
                toggleSticky(() -> ctrlHeld, value -> ctrlHeld = value,
                        () -> ctrlUsed, value -> ctrlUsed = value,
                        (short) KeyboardTranslator.VK_LCONTROL);
                break;
            case ALT:
                toggleSticky(() -> altHeld, value -> altHeld = value,
                        () -> altUsed, value -> altUsed = value,
                        (short) KeyboardTranslator.VK_LMENU);
                break;
            case WIN:
                toggleSticky(() -> winHeld, value -> winHeld = value,
                        () -> winUsed, value -> winUsed = value,
                        (short) KeyboardTranslator.VK_LWIN);
                break;
            case CAPS:
                capsLock = !capsLock;
                break;
            default:
                break;
        }
        refreshKeyAppearance();
    }

    private interface BoolGet {
        boolean get();
    }

    private interface BoolSet {
        void set(boolean value);
    }

    private void toggleSticky(BoolGet held, BoolSet setHeld, BoolGet used, BoolSet setUsed, short vk) {
        if (held.get()) {
            setHeld.set(false);
            if (!used.get()) {
                sendSolo(vk);
            }
            setUsed.set(false);
        } else {
            setHeld.set(true);
            setUsed.set(false);
        }
    }

    private int computeKeyRowHeight() {
        int screenHeight = game.getResources().getDisplayMetrics().heightPixels;
        int panelMax = (int) (screenHeight * 0.62f);
        int rowsArea = panelMax - dp(124);
        return Math.max(dp(34), Math.min(dp(50), rowsArea / 5));
    }

    private void sendChar(int androidKeyCode, boolean forceShift, boolean letter) {
        if (!game.connected) {
            return;
        }
        short vk = game.toVkCode(androidKeyCode);
        if (vk == 0) {
            return;
        }
        ArrayList<Short> combo = new ArrayList<>();
        if (ctrlHeld) {
            combo.add((short) KeyboardTranslator.VK_LCONTROL);
            ctrlUsed = true;
        }
        if (altHeld) {
            combo.add((short) KeyboardTranslator.VK_LMENU);
            altUsed = true;
        }
        if (winHeld) {
            combo.add((short) KeyboardTranslator.VK_LWIN);
            winUsed = true;
        }

        boolean wantShift = forceShift || shiftHeld || (capsLock && letter);
        if (capsLock && shiftHeld && letter) {
            wantShift = forceShift;
        }
        if (wantShift) {
            combo.add((short) KeyboardTranslator.VK_LSHIFT);
            if (shiftHeld) {
                shiftUsed = true;
            }
        }
        combo.add(vk);
        game.sendKeys(toArray(combo));

        if (shiftHeld && !capsLock && !forceShift) {
            shiftHeld = false;
            shiftUsed = false;
            refreshKeyAppearance();
        }
    }

    private void sendSolo(short vk) {
        if (!game.connected) {
            return;
        }
        game.sendKeys(new short[]{vk});
    }

    private short[] toArray(List<Short> values) {
        short[] keys = new short[values.size()];
        for (int i = 0; i < values.size(); i++) {
            keys[i] = values.get(i);
        }
        return keys;
    }

    private boolean lettersShifted() {
        return shiftHeld || capsLock;
    }

    private int dp(int value) {
        return Math.round(value * game.getResources().getDisplayMetrics().density);
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    private static boolean isBackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK;
    }

    private static boolean isShortcutKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_X
                || keyCode == KeyEvent.KEYCODE_BUTTON_Y
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1
                || keyCode == KeyEvent.KEYCODE_BUTTON_R1;
    }

    private static KeyDef letter(String label, int keyCode) {
        return new KeyDef(label, label.toUpperCase(), keyCode, false, true, 1f, Action.CHAR, 0);
    }

    private static KeyDef ch(String label, int keyCode) {
        return new KeyDef(label, label, keyCode, false, false, 1f, Action.CHAR, 0);
    }

    private static KeyDef ch(String label, String shifted, int keyCode) {
        return new KeyDef(label, shifted, keyCode, false, false, 1f, Action.CHAR, 0);
    }

    private static KeyDef shifted(String label, int keyCode) {
        return new KeyDef(label, label, keyCode, true, false, 1f, Action.CHAR, 0);
    }

    private KeyDef action(int labelRes, Action action, float weight) {
        return new KeyDef(game.getString(labelRes), game.getString(labelRes),
                0, false, false, weight, action, labelRes);
    }

    private enum Action {
        CHAR, SHIFT, CTRL, ALT, WIN, CAPS, SPACE, ENTER, BACKSPACE, TAB, ESC,
        INSERT, DELETE, HOME, END, PAGE_UP, PAGE_DOWN, PRINT, PAUSE,
        PAGE_LETTERS, PAGE_SYMBOLS, PAGE_CONTROLS, CLOSE
    }

    private static final class KeyDef {
        final String label;
        final String shiftedLabel;
        final int keyCode;
        final boolean forceShift;
        final boolean letter;
        final float weight;
        final Action action;
        final int labelRes;

        KeyDef(String label, String shiftedLabel, int keyCode, boolean forceShift,
               boolean letter, float weight, Action action, int labelRes) {
            this.label = label;
            this.shiftedLabel = shiftedLabel;
            this.keyCode = keyCode;
            this.forceShift = forceShift;
            this.letter = letter;
            this.weight = weight;
            this.action = action;
            this.labelRes = labelRes;
        }
    }

    private final class KeyView {
        final KeyDef def;
        final TextView view;

        KeyView(KeyDef def) {
            this.def = def;
            view = new TextView(rowsContainer.getContext());
            view.setId(View.generateViewId());
            view.setTag(this);
            view.setGravity(android.view.Gravity.CENTER);
            view.setText(def.label);
            view.setTextColor(AppCompatResources.getColorStateList(game, R.color.dash_on_focus_text));
            view.setTextSize(def.action == Action.CHAR && def.letter ? 17 : 13);
            view.setTypeface(ResourcesCompat.getFont(game, def.action == Action.CHAR && def.letter ?
                    R.font.albert_sans_semibold : R.font.albert_sans_bold));
            view.setBackgroundResource(R.drawable.console_keyboard_key_background);
            view.setClickable(true);
            view.setFocusable(true);
            view.setFocusableInTouchMode(false);
            view.setSoundEffectsEnabled(true);
            view.setMaxLines(1);
            view.setIncludeFontPadding(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.setDefaultFocusHighlightEnabled(false);
            }
            view.setOnClickListener(v -> fire());
            view.setOnKeyListener((v, keyCode, event) -> handleKey(keyCode, event));
        }

        boolean isRepeatable() {
            return def.action == Action.BACKSPACE
                    || def.action == Action.DELETE
                    || def.action == Action.CHAR && isArrow(def.keyCode);
        }

        boolean handleKey(int keyCode, KeyEvent event) {
            if (!isConfirmKey(keyCode)) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0 && isRepeatable()) {
                    fire();
                    repeatingKey = this;
                    handler.postDelayed(repeatRunnable, REPEAT_START_MS);
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean wasRepeating = repeatingKey == this;
                handler.removeCallbacks(repeatRunnable);
                repeatingKey = null;
                if (!wasRepeating) {
                    view.performClick();
                }
                return true;
            }
            return true;
        }

        void fire() {
            switch (def.action) {
                case CHAR:
                    sendChar(def.keyCode, def.forceShift, def.letter);
                    break;
                case SHIFT:
                case CTRL:
                case ALT:
                case WIN:
                case CAPS:
                    toggleModifier(def.action);
                    break;
                case SPACE:
                    sendChar(KeyEvent.KEYCODE_SPACE, false, false);
                    break;
                case ENTER:
                    sendChar(KeyEvent.KEYCODE_ENTER, false, false);
                    break;
                case BACKSPACE:
                    sendChar(KeyEvent.KEYCODE_DEL, false, false);
                    break;
                case TAB:
                    sendChar(KeyEvent.KEYCODE_TAB, false, false);
                    break;
                case ESC:
                    sendChar(KeyEvent.KEYCODE_ESCAPE, false, false);
                    break;
                case INSERT:
                    sendChar(KeyEvent.KEYCODE_INSERT, false, false);
                    break;
                case DELETE:
                    sendChar(KeyEvent.KEYCODE_FORWARD_DEL, false, false);
                    break;
                case HOME:
                    sendChar(KeyEvent.KEYCODE_MOVE_HOME, false, false);
                    break;
                case END:
                    sendChar(KeyEvent.KEYCODE_MOVE_END, false, false);
                    break;
                case PAGE_UP:
                    sendChar(KeyEvent.KEYCODE_PAGE_UP, false, false);
                    break;
                case PAGE_DOWN:
                    sendChar(KeyEvent.KEYCODE_PAGE_DOWN, false, false);
                    break;
                case PRINT:
                    sendChar(KeyEvent.KEYCODE_SYSRQ, false, false);
                    break;
                case PAUSE:
                    sendChar(KeyEvent.KEYCODE_BREAK, false, false);
                    break;
                case PAGE_LETTERS:
                    setPage(PAGE_LETTERS, true);
                    break;
                case PAGE_SYMBOLS:
                    setPage(PAGE_SYMBOLS, true);
                    break;
                case PAGE_CONTROLS:
                    setPage(PAGE_CONTROLS, true);
                    break;
                case CLOSE:
                    dismiss();
                    break;
            }
        }

        void refresh() {
            String label = def.label;
            if (def.letter && lettersShifted()) {
                label = def.shiftedLabel;
            } else if (shiftHeld && !def.forceShift) {
                label = def.shiftedLabel;
            }
            view.setText(label);
            view.setActivated(isActiveModifier());
        }

        boolean isActiveModifier() {
            switch (def.action) {
                case SHIFT:
                    return shiftHeld;
                case CTRL:
                    return ctrlHeld;
                case ALT:
                    return altHeld;
                case WIN:
                    return winHeld;
                case CAPS:
                    return capsLock;
                default:
                    return false;
            }
        }
    }

    private static boolean isArrow(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }
}
