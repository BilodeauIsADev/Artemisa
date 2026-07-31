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
    private TextView menuSubtitle;
    private TextView menuBackActionLabel;
    private LinearLayout menuItems;
    private Runnable currentBackAction;
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
        if (contentView.getBackground() != null) {
            int opacity = PreferenceConfiguration.readPreferences(game).quickMenuOpacity;
            contentView.getBackground().mutate().setAlpha(Math.round(opacity * 2.55f));
        }
        menuSubtitle = contentView.findViewById(R.id.gameMenuSubtitle);
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
            window.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.dimAmount = 0.58f;
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
        menuSubtitle.animate().cancel();
        if (!animateTransition) {
            bindMenuContent(title, options, backAction);
            return;
        }

        menuItems.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        menuItems.animate()
                .alpha(0f)
                .translationX(-dpToPx(12))
                .setDuration(70)
                .withEndAction(() -> {
                    if (transitionGeneration != menuTransitionGeneration || menuItems == null) {
                        return;
                    }
                    bindMenuContent(title, options, backAction);
                    menuItems.setAlpha(0f);
                    menuItems.setTranslationX(dpToPx(12));
                    menuItems.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(120)
                            .start();
                })
                .start();
        menuSubtitle.animate().alpha(0f).setDuration(70).withEndAction(() -> {
            if (transitionGeneration == menuTransitionGeneration && menuSubtitle != null) {
                menuSubtitle.setText(title);
                menuSubtitle.animate().alpha(1f).setDuration(120).start();
            }
        }).start();
    }

    private void bindMenuContent(String title, MenuOption[] options, Runnable backAction) {
        Context themedContext = menuItems.getContext();
        menuSubtitle.setText(title);
        menuBackActionLabel.setText(backAction == null ? R.string.console_close : R.string.console_back);
        menuItems.removeAllViews();

        View firstFocusable = null;
        for (MenuOption option : options) {
            View row = LayoutInflater.from(themedContext).inflate(R.layout.game_menu_option, menuItems, false);
            ImageView icon = row.findViewById(R.id.gameMenuOptionIcon);
            TextView label = row.findViewById(R.id.gameMenuOptionLabel);
            TextView status = row.findViewById(R.id.gameMenuOptionStatus);

            label.setText(option.label);
            if (option.iconRes != 0) {
                icon.setImageResource(option.iconRes);
            } else {
                icon.setVisibility(View.GONE);
            }

            if (TextUtils.isEmpty(option.status)) {
                status.setVisibility(View.GONE);
            } else {
                status.setText(option.status);
            }

            if (option.destructive) {
                row.setBackgroundResource(R.drawable.console_menu_row_danger_background);
                label.setTextColor(game.getResources().getColor(R.color.console_danger));
            }

            row.setOnClickListener(view -> {
                if (option.dismissMenuOnRun) {
                    hideMenu();
                }
                run(option);
            });
            row.setOnKeyListener((view, keyCode, event) -> {
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
            menuItems.addView(row);
            if (firstFocusable == null) {
                firstFocusable = row;
            }
        }
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
        menuSubtitle = null;
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
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
                game::toggleKeyboard));
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
                R.drawable.ic_console_performance,
                currentMode == Game.PERFORMANCE_OVERLAY_OFF ? getString(R.string.console_on) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_OFF)));
        options.add(MenuOption.console(
                getString(R.string.console_performance_simple),
                R.drawable.ic_console_performance,
                currentMode == Game.PERFORMANCE_OVERLAY_SIMPLE ? getString(R.string.console_on) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_SIMPLE)));
        options.add(MenuOption.console(
                getString(R.string.console_performance_advanced),
                R.drawable.ic_console_performance,
                currentMode == Game.PERFORMANCE_OVERLAY_ADVANCED ? getString(R.string.console_on) : null,
                false,
                true,
                () -> game.setPerformanceOverlayMode(Game.PERFORMANCE_OVERLAY_ADVANCED)));

        showMenuDialog(
                getString(R.string.console_performance_mode),
                options.toArray(new MenuOption[0]),
                () -> showMenu(device));
    }

    public void showMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();

        options.add(MenuOption.consoleNavigation(
                getString(R.string.console_performance_overlay),
                R.drawable.ic_console_performance,
                getPerformanceModeLabel(),
                () -> showPerformanceMenu(device)));

        if (game.isControllerMouseEmulationAvailable() && device != null) {
            options.add(MenuOption.console(
                    getString(R.string.console_mouse_mode),
                    R.drawable.ic_console_mouse,
                    getString(device.isMouseEmulationActive() ? R.string.console_on : R.string.console_off),
                    false,
                    true,
                    device::toggleMouseEmulation));
        }

        options.add(MenuOption.consoleNavigation(
                getString(R.string.console_advanced_settings),
                R.drawable.ic_console_controller,
                null,
                () -> showAdvancedMenu(device)));

        options.add(MenuOption.consoleNavigation(
                getString(R.string.console_keyboard_shortcuts),
                R.drawable.ic_android_keyboard,
                null,
                () -> showSpecialKeysMenu(() -> showMenu(device))));

        options.add(MenuOption.console(
                getString(R.string.game_menu_disconnect),
                R.drawable.ic_console_link,
                null,
                false,
                false,
                game::disconnect));

        options.add(MenuOption.consoleNavigation(
                getString(R.string.console_end_stream),
                R.drawable.ic_console_stop,
                null,
                () -> showEndStreamConfirmation(device)));

        showMenuDialog(
                game.getString(R.string.console_streaming_on, game.getStreamingComputerName()),
                options.toArray(new MenuOption[0]));
    }

    private void showEndStreamConfirmation(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();
        options.add(MenuOption.console(
                getString(R.string.console_end_stream),
                R.drawable.ic_console_stop,
                null,
                true,
                false,
                game::endStream));
        options.add(MenuOption.console(
                getString(R.string.console_keep_playing),
                R.drawable.ic_console_resume,
                null,
                false,
                false,
                () -> {}));
        showMenuDialog(
                getString(R.string.console_end_stream_message),
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
