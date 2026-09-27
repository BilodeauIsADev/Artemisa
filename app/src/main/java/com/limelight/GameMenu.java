package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;
        private final int iconRes;
        private final String status;
        private final boolean destructive;
        private final boolean dismissMenuOnRun;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this(label, withGameFocus, runnable, 0, null, false, true);
        }

        private MenuOption(String label, boolean withGameFocus, Runnable runnable,
                           int iconRes, String status, boolean destructive,
                           boolean dismissMenuOnRun) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
            this.iconRes = iconRes;
            this.status = status;
            this.destructive = destructive;
            this.dismissMenuOnRun = dismissMenuOnRun;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }

        private static MenuOption console(String label, int iconRes, String status,
                                          boolean destructive, boolean withGameFocus,
                                          Runnable runnable) {
            return new MenuOption(label, withGameFocus, runnable, iconRes, status,
                    destructive, true);
        }

        private static MenuOption navigation(String label, Runnable runnable) {
            return new MenuOption(label, false, runnable, 0, null, false, false);
        }

        private static MenuOption consoleNavigation(String label, int iconRes, String status,
                                                     Runnable runnable) {
            return new MenuOption(label, false, runnable, iconRes, status, false, false);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;

    private Dialog currentDialog;
    private TextView menuTitle;
    private TextView menuSubtitle;
    private View menuScroll;
    private TextView menuBackActionLabel;
    private LinearLayout menuItems;
    private Runnable currentBackAction;
    private String pendingSubtitle;
    private int pendingFocusIndex;
    private int menuTransitionGeneration;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }


    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        showMenuDialog(title, options, null);
    }

    private void showMenuDialog(String title, MenuOption[] options, Runnable backAction) {
        boolean animateTransition = currentDialog != null && currentDialog.isShowing();
        if (!animateTransition) {
            createMenuDialog();
        }

        updateMenuContent(title, options, backAction, animateTransition);
    }

    private void createMenuDialog() {
        int themeResId = game.getApplicationInfo().theme;
        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);

        Dialog dialog = new Dialog(themedContext);
        FrameLayout inflationParent = new FrameLayout(themedContext);
        View contentView = LayoutInflater.from(themedContext)
                .inflate(R.layout.dialog_game_menu, inflationParent, false);
        View sheet = contentView.findViewById(R.id.gameMenuSheet);
        if (sheet.getBackground() != null) {
            int opacity = PreferenceConfiguration.readPreferences(game).quickMenuOpacity;
            sheet.getBackground().mutate().setAlpha(Math.round(opacity * 2.55f));
        }
        menuTitle = contentView.findViewById(R.id.gameMenuTitle);
        menuSubtitle = contentView.findViewById(R.id.gameMenuSubtitle);
        menuScroll = contentView.findViewById(R.id.gameMenuScroll);
        menuItems = contentView.findViewById(R.id.gameMenuItems);
        menuBackActionLabel = contentView.findViewById(R.id.gameMenuBackActionLabel);

        dialog.setContentView(contentView);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            boolean isBackAction = keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                    keyCode == KeyEvent.KEYCODE_BACK;
            if (event.getAction() == KeyEvent.ACTION_DOWN && isBackAction) {
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && isBackAction) {
                navigateBack();
                return true;
            }
            return false;
        });
        dialog.setOnDismissListener(ignored -> {
            if (currentDialog == dialog) {
                clearMenuDialogState();
            }
        });

        currentDialog = dialog;
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.35f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void updateMenuContent(String title, MenuOption[] options, Runnable backAction,
                                   boolean animateTransition) {
        currentBackAction = backAction;
        menuTransitionGeneration++;
        int transitionGeneration = menuTransitionGeneration;

        menuItems.animate().cancel();
        menuTitle.animate().cancel();
        if (!animateTransition) {
            bindMenuContent(title, options, backAction);
            return;
        }

        menuItems.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        menuItems.animate()
                .alpha(0f)
                .translationY(dpToPx(8))
                .setDuration(70)
                .withEndAction(() -> {
                    if (transitionGeneration != menuTransitionGeneration || menuItems == null) {
                        return;
                    }
                    bindMenuContent(title, options, backAction);
                    menuItems.setAlpha(0f);
                    menuItems.setTranslationY(dpToPx(8));
                    menuItems.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(140)
                            .start();
                })
                .start();
    }

    private static final int CARD_COLUMNS_MAX = 6;
    private static final int COMPACT_COLUMNS = 4;
    private static final int COMPACT_VISIBLE_ROWS = 4;

    private void bindMenuContent(String title, MenuOption[] options, Runnable backAction) {
        Context themedContext = menuItems.getContext();
        menuTitle.setText(title);
        // A menu can replace the "Streaming from" line once, for example with a warning
        menuSubtitle.setText(pendingSubtitle != null ? pendingSubtitle :
                game.getString(R.string.dash_streaming_from, game.getStreamingComputerName()));
        pendingSubtitle = null;
        // Set per menu; destructive confirmations focus the safe choice
        int focusIndex = pendingFocusIndex;
        pendingFocusIndex = 0;
        menuBackActionLabel.setText(backAction == null ? R.string.dash_close : R.string.dash_back);
        menuItems.removeAllViews();

        // Short menus are one row of cards; long ones become a grid of compact cards.
        boolean compact = options.length > CARD_COLUMNS_MAX;
        int columns = compact ? COMPACT_COLUMNS : Math.max(1, options.length);
        int layout = compact ? R.layout.game_menu_option_compact : R.layout.game_menu_option;
        int gap = dpToPx(10);

        View firstFocusable = null;
        LinearLayout row = null;
        int rows = 0;
        for (int i = 0; i < options.length; i++) {
            MenuOption option = options[i];
            if (i % columns == 0) {
                row = new LinearLayout(themedContext);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (rows > 0) {
                    rowParams.topMargin = gap;
                }
                menuItems.addView(row, rowParams);
                rows++;
            }

            View card = LayoutInflater.from(themedContext).inflate(layout, row, false);
            ImageView icon = card.findViewById(R.id.gameMenuOptionIcon);
            TextView label = card.findViewById(R.id.gameMenuOptionLabel);
            TextView status = card.findViewById(R.id.gameMenuOptionStatus);

            label.setText(option.label);
            if (option.iconRes != 0) {
                icon.setImageResource(option.iconRes);
            } else {
                icon.setVisibility(View.GONE);
            }

            if (TextUtils.isEmpty(option.status)) {
                status.setVisibility(compact ? View.GONE : View.INVISIBLE);
            } else {
                status.setText(option.status);
            }

            if (option.destructive) {
                int danger = game.getResources().getColor(R.color.dash_stop);
                label.setTextColor(danger);
                icon.setColorFilter(danger);
            }

            card.setOnClickListener(view -> {
                if (option.dismissMenuOnRun) {
                    hideMenu();
                }
                run(option);
            });
            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_UP) {
                    return keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                            keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                            keyCode == KeyEvent.KEYCODE_BACK;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    view.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                        keyCode == KeyEvent.KEYCODE_BACK) {
                    navigateBack();
                    return true;
                }
                return false;
            });

            LinearLayout.LayoutParams cardParams = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (i % columns != columns - 1) {
                cardParams.setMarginEnd(gap);
            }
            row.addView(card, cardParams);
            if (firstFocusable == null || i == focusIndex) {
                firstFocusable = card;
            }
        }

        // Keep the last grid row's cards the same width as the others
        if (compact && row != null) {
            int filled = options.length % columns;
            for (int i = filled; filled != 0 && i < columns; i++) {
                View spacer = new View(themedContext);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
                if (i != columns - 1) {
                    spacerParams.setMarginEnd(gap);
                }
                row.addView(spacer, spacerParams);
            }
        }

        // Long grids scroll inside the sheet instead of covering the stream
        ViewGroup.LayoutParams scrollParams = menuScroll.getLayoutParams();
        scrollParams.height = compact && rows > COMPACT_VISIBLE_ROWS ?
                dpToPx(COMPACT_VISIBLE_ROWS * 56 + 12) : ViewGroup.LayoutParams.WRAP_CONTENT;
        menuScroll.setLayoutParams(scrollParams);
        menuScroll.scrollTo(0, 0);

        menuItems.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        if (firstFocusable != null) {
            firstFocusable.post(firstFocusable::requestFocus);
        }
    }

    private void navigateBack() {
        if (currentBackAction == null) {
            hideMenu();
        }
        else {
            currentBackAction.run();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * game.getResources().getDisplayMetrics().density);
    }

    private void clearMenuDialogState() {
        menuTransitionGeneration++;
        currentDialog = null;
        menuTitle = null;
        menuSubtitle = null;
        menuScroll = null;
        menuBackActionLabel = null;
        menuItems = null;
        currentBackAction = null;
    }

    private void showSpecialKeysMenu(Runnable backAction) {
        List<MenuOption> options = new ArrayList<>();

        if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        // Import custom shortcuts
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                    for (KeyConfigHelper.Shortcut sc : data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];

                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;

                            if (code.startsWith("0x")) {               // literal hex value
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {                                   // unsupported
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }

                        // Whatever MenuOption looks like in your project
                        MenuOption option = new MenuOption(sc.name, () -> sendKeys(keyCodes));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(
                getString(R.string.game_menu_send_keys),
                options.toArray(new MenuOption[0]),
                backAction);
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_toggle_floating_button), true, game::toggleFloatingButtonVisibility));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, game::toggleKeyboardController));
        if (!game.isOnExternalDisplay()) {
            options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, game::toggleVirtualController));
        }
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, game::toggleFullKeyboard));
        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true, () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

        options.add(MenuOption.navigation(
                getString(R.string.game_menu_send_keys),
                () -> showSpecialKeysMenu(() -> showAdvancedMenu(device))));

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, game::switchTouchSensitivity));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), false,
                game::showGamepadKeyboard));
        options.add(new MenuOption(
                getString(game.isZoomModeEnabled() ?
                        R.string.game_menu_disable_zoom_mode :
                        R.string.game_menu_enable_zoom_mode),
                true,
                game::toggleZoomMode));
        if (dialogScreenContext == game) {
            options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true,
                    game::rotateScreen));
        }
        options.add(new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                () -> game.sendClipboard(true)));
        options.add(new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                () -> game.getClipboard(0)));
        options.add(MenuOption.navigation(getString(R.string.game_menu_server_cmd), () -> {
            ArrayList<String> serverCmds = game.getServerCmds();
            if (serverCmds.isEmpty()) {
                int themeResId = game.getApplicationInfo().theme;
                Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
                new AlertDialog.Builder(themedContext)
                        .setTitle(R.string.game_dialog_title_server_cmd_empty)
                        .setMessage(R.string.game_dialog_message_server_cmd_empty)
                        .show();
            } else {
                showServerCmd(serverCmds, () -> showAdvancedMenu(device));
            }
        }));
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(
                getString(R.string.console_advanced_settings),
                options.toArray(new MenuOption[0]),
                () -> showMenu(device));
    }

    private void showServerCmd(ArrayList<String> serverCmds, Runnable backAction) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        };

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(
                getString(R.string.game_menu_server_cmd),
                options.toArray(new MenuOption[0]),
                backAction);
    }

    private String getPerformanceModeLabel() {
        switch (game.getPerformanceOverlayMode()) {
            case Game.PERFORMANCE_OVERLAY_SIMPLE:
                return getString(R.string.console_performance_simple);
            case Game.PERFORMANCE_OVERLAY_ADVANCED:
                return getString(R.string.console_performance_advanced);
            default:
                return getString(R.string.console_off);
        }
    }

    private void showPerformanceMenu(GameInputDevice device) {
        int currentMode = game.getPerformanceOverlayMode();
        List<MenuOption> options = new ArrayList<>();

        options.add(MenuOption.console(
                getString(R.string.console_performance_off),
                R.drawable.ic_dash_eyeoff,
                currentMode == Game.PERFORMANCE_OVERLAY_OFF ? getString(R.string.dash_current) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_OFF)));
        options.add(MenuOption.console(
                getString(R.string.console_performance_simple),
                R.drawable.ic_dash_chart,
                currentMode == Game.PERFORMANCE_OVERLAY_SIMPLE ? getString(R.string.dash_current) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_SIMPLE)));
        options.add(MenuOption.console(
                getString(R.string.console_performance_advanced),
                R.drawable.ic_dash_layers,
                currentMode == Game.PERFORMANCE_OVERLAY_ADVANCED ? getString(R.string.dash_current) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_ADVANCED)));

        showMenuDialog(
                getString(R.string.dash_performance),
                options.toArray(new MenuOption[0]),
                () -> showMenu(device));
    }

    public void showMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(MenuOption.consoleNavigation(
                getString(R.string.dash_performance),
                R.drawable.ic_dash_chart,
                getPerformanceModeLabel(),
                () -> showPerformanceMenu(device)));

        if (game.isControllerMouseEmulationAvailable() && device != null) {
            options.add(MenuOption.console(
                    getString(R.string.console_mouse_mode),
                    R.drawable.ic_dash_mouse,
                    getString(device.isMouseEmulationActive() ? R.string.console_on : R.string.console_off),
                    false,
                    true,
                    device::toggleMouseEmulation));
        }

        options.add(MenuOption.console(
                getString(R.string.dash_keyboard),
                R.drawable.ic_dash_keyboard,
                getString(R.string.dash_keyboard_type_on_pc),
                false,
                false,
                game::showGamepadKeyboard));

        options.add(MenuOption.consoleNavigation(
                getString(R.string.dash_advanced),
                R.drawable.ic_dash_command,
                getString(R.string.dash_shortcuts_status),
                () -> showAdvancedMenu(device)));

        options.add(MenuOption.console(
                getString(R.string.game_menu_disconnect),
                R.drawable.ic_dash_unlink,
                getString(R.string.dash_disconnect_status),
                false,
                false,
                game::disconnect));

        options.add(new MenuOption(
                getString(R.string.console_end_stream),
                false,
                () -> showEndStreamConfirmation(device),
                R.drawable.ic_dash_power,
                getString(R.string.dash_end_stream_status),
                true,
                false));

        showMenuDialog(
                game.getStreamingAppName(),
                options.toArray(new MenuOption[0]));
    }

    private void showEndStreamConfirmation(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();
        options.add(MenuOption.console(
                getString(R.string.console_end_stream),
                R.drawable.ic_dash_power,
                null,
                true,
                false,
                game::endStream));
        options.add(MenuOption.console(
                getString(R.string.console_keep_playing),
                R.drawable.ic_dash_play,
                null,
                false,
                false,
                () -> {}));
        pendingSubtitle = getString(R.string.console_end_stream_message);
        // Land on "Keep playing" so a double press of A cannot end the session
        pendingFocusIndex = 1;
        showMenuDialog(
                getString(R.string.dash_end_stream_title),
                options.toArray(new MenuOption[0]),
                () -> showMenu(device));
    }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    @Override
    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }
}
