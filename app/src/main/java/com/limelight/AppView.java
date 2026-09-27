package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.profiles.ProfilesManager;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.ui.ConsoleMenu;
import com.limelight.ui.HorizontalGameListView;
import com.limelight.utils.ArtHelper;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.xmlpull.v1.XmlPullParserException;

public class AppView extends AppCompatActivity implements AdapterFragmentCallbacks {
    private AppGridAdapter appGridAdapter;
    private String uuidString;
    private ShortcutHelper shortcutHelper;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    private HorizontalGameListView tileRow;
    private ImageView backdropView;
    private ImageView keyArtView;
    private TextView heroTitle;
    private TextView heroMeta;
    private TextView playLabel;
    private View playButton;
    private View moreButton;
    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private int artGeneration;

    private PreferenceConfiguration prefConfig;

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int START_WITH_QUIT = 4;
    private final static int VIEW_DETAILS_ID = 5;
    private final static int CREATE_SHORTCUT_ID = 6;
    private final static int EXPORT_LAUNCHER_FILE_ID = 7;
    private final static int HIDE_APP_ID = 8;
    private final static int START_WITH_VDISPLAY = 20;
    private final static int START_WITH_QUIT_VDISPLAY = 21;

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                return;
                            }

                            // Despite my best efforts to catch all conditions that could
                            // cause the activity to be destroyed when we try to commit
                            // I haven't been able to, so we have this try-catch block.
                            try {
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                                        .commitAllowingStateLoss();
                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateGridLayout(PreferenceConfiguration.readPreferences(this), true);
    }

    private void updateGridLayout(PreferenceConfiguration updatedPrefs, boolean force) {
        prefConfig = updatedPrefs;

        // If the adapter isn't initialized yet, it will use the latest preferences when created.
        if (appGridAdapter == null || !force) {
            updateHero();
            return;
        }

        appGridAdapter.updateLayoutWithPreferences(this, prefConfig);
        try {
            getFragmentManager().beginTransaction()
                    .replace(R.id.appFragmentContainer, new AdapterFragment())
                    .commitAllowingStateLoss();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, R.string.lost_connection, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, R.string.scut_not_paired, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_app_view);

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        UiHelper.notifyNewRootView(this, false);

        // Setup the top bar
        findViewById(R.id.profilesButton)
            .setOnClickListener(v -> startActivity(new Intent(this, ProfilesActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(v -> openSettings());

        backdropView = findViewById(R.id.libraryBackdrop);
        backdropView.setColorFilter(ArtHelper.backdropFilter(0.5f));
        keyArtView = findViewById(R.id.libraryKeyArt);
        heroTitle = findViewById(R.id.libraryTitle);
        heroMeta = findViewById(R.id.libraryMeta);
        playLabel = findViewById(R.id.libraryPlayLabel);
        playButton = findViewById(R.id.libraryPlayButton);
        moreButton = findViewById(R.id.libraryMoreButton);

        playButton.setOnClickListener(v -> {
            int position = getFocusedAppPosition();
            if (position >= 0) {
                handleAppSelection(playButton, position);
            }
        });
        moreButton.setOnClickListener(v -> {
            int position = getFocusedAppPosition();
            if (position >= 0) {
                showAppOptions(position, moreButton);
            }
        });
        View.OnKeyListener backToRow = (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN
                    && tileRow != null) {
                return tileRow.requestFocusOnCurrent();
            }
            return false;
        };
        playButton.setOnKeyListener(backToRow);
        moreButton.setOnKeyListener(backToRow);

        showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getIntent().getStringExtra(NAME_EXTRA);

        TextView label = findViewById(R.id.appListText);
        setTitle(computerName);
        label.setText(computerName);

        this.prefConfig = PreferenceConfiguration.readPreferences(this);
        heroTitle.setText(computerName);
        updateHero();

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void openSettings() {
        startActivity(new Intent(this, StreamSettings.class));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            int position = getFocusedAppPosition();
            if (position >= 0) {
                showAppOptions(position, moreButton);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private int getFocusedAppPosition() {
        if (appGridAdapter == null || tileRow == null || appGridAdapter.getCount() == 0) {
            return -1;
        }
        return Math.min(tileRow.getFocusedPosition(), appGridAdapter.getCount() - 1);
    }

    private static String resolutionLabel(int width, int height) {
        switch (height) {
            case 2160: return "4K";
            case 1440: return "1440p";
            case 1080: return "1080p";
            case 720: return "720p";
            default: return width + "×" + height;
        }
    }

    // Title, meta line and Play/Resume label for the focused game.
    private void updateHero() {
        if (heroMeta == null || prefConfig == null) {
            return;
        }
        String meta = getString(R.string.dash_library_meta,
                getTitle(),
                resolutionLabel(prefConfig.width, prefConfig.height),
                Math.round(prefConfig.fps),
                Math.round(prefConfig.bitrate / 1000f));

        int position = getFocusedAppPosition();
        boolean running = false;
        if (position >= 0) {
            AppObject app = (AppObject) appGridAdapter.getItem(position);
            heroTitle.setText(app.app.getAppName());
            running = app.isRunning;
        }
        heroMeta.setText(running ? getString(R.string.dash_running) + " · " + meta : meta);
        playLabel.setText(running ? R.string.dash_resume : R.string.dash_play);
    }

    // The backdrop and key art follow the focused game.
    private void onGameFocused(View tile, int position) {
        updateHero();
        if (computer == null || position >= appGridAdapter.getCount()) {
            return;
        }

        final int generation = ++artGeneration;
        final AppObject app = (AppObject) appGridAdapter.getItem(position);
        final Bitmap tileBitmap = ArtHelper.bitmapOf(tile.findViewById(R.id.grid_image));
        final String uuid = computer.uuid;
        artExecutor.execute(() -> {
            Bitmap cover = ArtHelper.loadCachedCover(this, uuid, app.app.getAppId(), 900);
            if (cover == null) {
                cover = tileBitmap;
            }
            final Bitmap keyArt = cover;
            final Bitmap blurred = ArtHelper.blur(cover);
            runOnUiThread(() -> {
                if (generation != artGeneration || isFinishing()) {
                    return;
                }
                ArtHelper.setBackdrop(backdropView, blurred);
                keyArtView.setImageBitmap(keyArt);
                keyArtView.setAlpha(0f);
                keyArtView.animate().alpha(0.92f).setDuration(220).start();
                backdropView.setAlpha(0.4f);
                backdropView.animate().alpha(1f).setDuration(260).start();
            });
        });
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.applist_refresh_title),
                getResources().getString(R.string.applist_refresh_msg), true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();
        artExecutor.shutdownNow();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateGridLayout(PreferenceConfiguration.readPreferences(this), false);

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        startComputerUpdates();

        ImageButton profilesButton = findViewById(R.id.profilesButton);
        // User report Samsung and Xiaomi devices have this problem
        // Why just these two brands have the most problems?
        if (profilesButton == null) {
            return;
        }
        String activeProfileName = ProfilesManager.getInstance().getActiveName();
        profilesButton.setContentDescription(activeProfileName.isEmpty()
                ? getString(R.string.profile_manager_choose_profile)
                : getString(R.string.profile_manager_active_profile, activeProfileName));
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ShortcutHelper.REQUEST_CODE_EXPORT_ART_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                ShortcutHelper.writeArtFileToUri(this, uri);
            } else {
                // Clear the content if the user cancelled or if there was an error before this point
                ShortcutHelper.artFileContentToExport = null;
                // Show "File export cancelled." toast only if the user explicitly cancelled.
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, R.string.file_export_cancelled, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showAppOptions(int position, View anchor) {
        if (position < 0 || position >= appGridAdapter.getCount()) {
            return;
        }

        AppObject selectedApp = (AppObject) appGridAdapter.getItem(position);
        ConsoleMenu menu = new ConsoleMenu(this, selectedApp.app.getAppName());

        if (lastRunningAppId == 0) {
            if (prefConfig.useVirtualDisplay) {
                menu.add(START_OR_RESUME_ID, getString(R.string.applist_menu_start_primarydisplay), R.drawable.ic_dash_monitor);
            } else {
                menu.add(START_WITH_VDISPLAY, getString(R.string.applist_menu_start_vdisplay), R.drawable.ic_dash_monitor);
            }
        } else {
            if (lastRunningAppId == selectedApp.app.getAppId()) {
                menu.add(START_OR_RESUME_ID, getString(R.string.applist_menu_resume), R.drawable.ic_dash_play);
                menu.addDestructive(QUIT_ID, getString(R.string.applist_menu_quit), R.drawable.ic_dash_power);
            }
            else {
                if (prefConfig.useVirtualDisplay) {
                    menu.add(START_WITH_QUIT_VDISPLAY, getString(R.string.applist_menu_quit_and_start), R.drawable.ic_dash_refresh);
                    menu.add(START_WITH_QUIT, getString(R.string.applist_menu_quit_and_start_primarydisplay), R.drawable.ic_dash_monitor);
                } else {
                    menu.add(START_WITH_QUIT, getString(R.string.applist_menu_quit_and_start), R.drawable.ic_dash_refresh);
                    menu.add(START_WITH_QUIT_VDISPLAY, getString(R.string.applist_menu_quit_and_start_vdisplay), R.drawable.ic_dash_monitor);
                }
            }
        }
        menu.addSeparator();

        // Only show the hide option if this is not the currently running app or it's already hidden
        if (lastRunningAppId != selectedApp.app.getAppId() || selectedApp.isHidden) {
            menu.addChecked(HIDE_APP_ID, getString(R.string.applist_menu_hide_app), R.drawable.ic_dash_eyeoff, selectedApp.isHidden);
        }

        View tile = tileRow != null ? tileRow.getFocusedItemView() : null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && tile != null) {
            // Only offer a shortcut once the box art has loaded
            Bitmap art = ArtHelper.bitmapOf(tile.findViewById(R.id.grid_image));
            if (art != null) {
                menu.add(CREATE_SHORTCUT_ID, getString(R.string.applist_menu_scut), R.drawable.ic_dash_pin);
            }
        }

        menu.add(EXPORT_LAUNCHER_FILE_ID, getString(R.string.applist_menu_export_launcher), R.drawable.ic_dash_external);
        menu.add(VIEW_DETAILS_ID, getString(R.string.applist_menu_details), R.drawable.ic_dash_info);

        menu.show(anchor, itemId -> handleAppMenuAction(itemId, selectedApp, tile));
    }

    private void handleAppMenuAction(int itemId, final AppObject app, View targetView) {
        switch (itemId) {
            case START_WITH_QUIT:
            case START_WITH_QUIT_VDISPLAY: {
                boolean withVDiaplay = itemId == START_WITH_QUIT_VDISPLAY;
                if (withVDiaplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                        AppView.this,
                        computer,
                        () -> UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                            @Override
                            public void run() {
                                ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true);
                            }
                        }, null),
                        null
                    );
                } else {
                    // Display a confirmation dialog first
                    UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                        @Override
                        public void run() {
                            ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, withVDiaplay);
                        }
                    }, null);
                }
                return;
            }

            case START_OR_RESUME_ID:
            case START_WITH_VDISPLAY: {
                boolean withVDiaplay = itemId == START_WITH_VDISPLAY;
                if (withVDiaplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                            AppView.this,
                            computer,
                            () -> ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true),
                            null
                    );
                } else {
                    // Resume is the same as start for us
                    ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, withVDiaplay);
                }
                return;
            }

            case QUIT_ID: {
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        suspendGridUpdates = true;
                        ServerHelper.doQuit(AppView.this, computer,
                                app.app, managerBinder, new Runnable() {
                                    @Override
                                    public void run() {
                                        // Trigger a poll immediately
                                        suspendGridUpdates = false;
                                        if (poller != null) {
                                            poller.pollNow();
                                        }
                                    }
                                });
                    }
                }, null);
                return;
            }

            case VIEW_DETAILS_ID: {
                Dialog.displayDialog(AppView.this, getResources().getString(R.string.title_details), app.app.toString(), false);
                return;
            }

            case HIDE_APP_ID: {
                if (app.isHidden) {
                    // Transitioning hidden to shown
                    hiddenAppIds.remove(app.app.getAppId());
                } else {
                    // Transitioning shown to hidden
                    hiddenAppIds.add(app.app.getAppId());
                }
                updateHiddenApps(false);
                return;
            }

            case CREATE_SHORTCUT_ID: {
                Bitmap appBits = targetView != null ? ArtHelper.bitmapOf(targetView.findViewById(R.id.grid_image)) : null;
                if (appBits == null || !shortcutHelper.createPinnedGameShortcut(computer, app.app, appBits)) {
                    Toast.makeText(AppView.this, getResources().getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show();
                }
                return;
            }

            case EXPORT_LAUNCHER_FILE_ID: {
                if (app.app.getAppUUID() == null || (app.app.getAppUUID() != null && app.app.getAppUUID().isEmpty())) {
                    UiHelper.displayConfirmationDialog(
                            AppView.this,
                            getResources().getString(R.string.title_export_sunshine_launcher_file),
                            getResources().getString(R.string.message_export_sunshine_launcher_file),
                            getResources().getString(R.string.proceed),
                            getResources().getString(R.string.cancel),
                            () -> shortcutHelper.exportLauncherFile(computer, app.app),
                            null
                    );
                } else {
                    shortcutHelper.exportLauncherFile(computer, app.app);
                }
                return;
            }

            default:
        }
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                    // Look through our current app list to tag the running app
                for (int i = 0; i < appGridAdapter.getCount(); i++) {
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                    updateHero();
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (int i = 0; i < appGridAdapter.getCount(); i++) {
                        AppObject existingApp = (AppObject) appGridAdapter.getItem(i);
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                int i = 0;
                while (i < appGridAdapter.getCount()) {
                    boolean foundExistingApp = false;
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, getString(R.string.app_removed_from_pc));
                        appGridAdapter.removeApp(existingApp);
                        updated = true;

                        // Check this same index again because the item at i+1 is now at i after
                        // the removal
                        continue;
                    }

                    // Move on to the next item
                    i++;
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.app_grid_view_horizontal;
    }

    @Override
    public void receiveAdapterView(View adapterView) {
        tileRow = (HorizontalGameListView) adapterView;
        tileRow.setNextFocusDownTarget(R.id.libraryPlayButton);
        tileRow.setOnItemFocusListener(this::onGameFocused);
        tileRow.setAdapter(appGridAdapter);
        tileRow.setOnItemClickListener(
                (view, position, id) -> handleAppSelection(view, position));
        tileRow.setOnItemLongClickListener((view, position, id) -> {
            showAppOptions(position, moreButton);
            return true;
        });
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        // The library is always a single row of tiles
    }

    private void handleAppSelection(View targetView, int position) {
        AppObject app = (AppObject) appGridAdapter.getItem(position);

        // Only open the context menu if something is running, otherwise start it.
        if (lastRunningAppId != 0) {
            if (prefConfig.resumeWithoutConfirm && lastRunningAppId == app.app.getAppId()) {
                ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
            }
            else {
                showAppOptions(position, moreButton);
            }
        }
        else if (prefConfig.useVirtualDisplay &&
                !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
            UiHelper.displayVdisplayConfirmationDialog(
                    AppView.this,
                    computer,
                    () -> ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, true),
                    null);
        }
        else {
            ServerHelper.doStart(AppView.this, app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
        }
    }

    public static class AppObject {
        public final NvApp app;
        public boolean isRunning;
        public boolean isHidden;

        public AppObject(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.app = app;
        }

        @Override
        public String toString() {
            return app.getAppName();
        }
    }
}
