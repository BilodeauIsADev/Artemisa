package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
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
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends AppCompatActivity implements AdapterFragmentCallbacks {
    private View noPcFoundLayout;
    private HorizontalGameListView tileRow;
    private View addComputerTile;
    private View homeHero;
    private TextView homeTitle;
    private TextView homeMeta;
    private TextView homeHintPrimary;
    private LinearLayout homeCovers;
    private ImageView homeBackdrop;
    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private int artGeneration;
    private String heroComputerUuid;
    private int heroGameCount = -1;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private ComputerDetails.AddressTuple pendingPairingAddress;
    private String pendingPairingPin, pendingPairingPassphrase;
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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
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

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }

        refreshProfileButton();
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;
    private final static int OPEN_MANAGEMENT_PAGE_ID = 20;
    private final static int PAIR_ID_OTP = 21;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this, false);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set the correct layout for the PC grid
        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

        // Setup the top bar and hero
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton helpButton = findViewById(R.id.helpButton);
        ImageButton profilesButton = findViewById(R.id.profilesButton);

        homeHero = findViewById(R.id.homeHero);
        homeTitle = findViewById(R.id.homeTitle);
        homeMeta = findViewById(R.id.homeMeta);
        homeHintPrimary = findViewById(R.id.homeHintPrimary);
        homeCovers = findViewById(R.id.homeCovers);
        homeBackdrop = findViewById(R.id.homeBackdrop);
        homeBackdrop.setColorFilter(ArtHelper.backdropFilter(0.75f));
        heroComputerUuid = null;

        addComputerTile = LayoutInflater.from(this).inflate(R.layout.dash_add_tile, null, false);
        View addComputerButton = addComputerTile;
        addComputerTile.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                homeHintPrimary.setText(R.string.dash_select);
            }
        });

        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        addComputerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PcView.this, AddComputerManually.class);
                startActivity(i);
            }
        });
        helpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpLauncher.launchSetupGuide(PcView.this);
            }
        });
        profilesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PcView.this, ProfilesActivity.class));
            }
        });

        // Amazon review didn't like the help button because the wiki was not entirely
        // navigable via the Fire TV remote (though the relevant parts were). Let's hide
        // it on Fire TV.
        if (getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            helpButton.setVisibility(View.GONE);
        }

        getFragmentManager().beginTransaction()
            .replace(R.id.pcFragmentContainer, new AdapterFragment())
            .commitAllowingStateLoss();

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        updateEmptyState();
        pcGridAdapter.notifyDataSetChanged();
    }

    private void openSettings() {
        startActivity(new Intent(this, StreamSettings.class));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (tileRow != null && pcGridAdapter.getCount() > 0 &&
                    (addComputerTile == null || !addComputerTile.hasFocus())) {
                showPcOptions(Math.min(tileRow.getFocusedPosition(), pcGridAdapter.getCount() - 1));
            }
            else {
                openSettings();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void updateEmptyState() {
        boolean empty = pcGridAdapter.getCount() == 0;
        noPcFoundLayout.setVisibility(empty ? View.VISIBLE : View.INVISIBLE);
        homeHero.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            homeHintPrimary.setText(R.string.dash_select);
        }
    }

    // The hero, covers and backdrop follow the focused computer.
    private void onComputerFocused(int position) {
        if (position < 0 || position >= pcGridAdapter.getCount()) {
            return;
        }
        ComputerDetails details = ((ComputerObject) pcGridAdapter.getItem(position)).details;
        boolean newComputer = !details.uuid.equals(heroComputerUuid);
        heroComputerUuid = details.uuid;
        if (newComputer) {
            heroGameCount = -1;
        }
        bindHero(details);
        if (newComputer) {
            loadComputerArt(details);
        }
    }

    private void bindHero(ComputerDetails details) {
        homeTitle.setText(details.name);

        List<String> parts = new ArrayList<>();
        int primaryHint;
        if (details.state == ComputerDetails.State.ONLINE) {
            parts.add(getString(R.string.dash_status_online));
            if (details.pairState == PairState.PAIRED) {
                parts.add(getString(details.runningGameId != 0 ?
                        R.string.dash_status_running : R.string.dash_status_paired));
                primaryHint = R.string.dash_open_library;
            }
            else {
                parts.add(getString(R.string.dash_status_not_paired));
                primaryHint = R.string.dash_pair;
            }
        }
        else {
            parts.add(getString(details.state == ComputerDetails.State.OFFLINE ?
                    R.string.dash_status_offline : R.string.dash_status_refreshing));
            primaryHint = R.string.dash_options;
        }
        if (heroGameCount >= 0) {
            parts.add(getResources().getQuantityString(R.plurals.dash_games_count, heroGameCount, heroGameCount));
        }
        homeMeta.setText(android.text.TextUtils.join(" · ", parts));
        if (addComputerTile == null || !addComputerTile.hasFocus()) {
            homeHintPrimary.setText(primaryHint);
            // When A already opens the options, the Menu hint would say the same thing
            int menuHint = primaryHint == R.string.dash_options ? View.GONE : View.VISIBLE;
            findViewById(R.id.homeHintMenuIcon).setVisibility(menuHint);
            findViewById(R.id.homeHintMenuLabel).setVisibility(menuHint);
        }
    }

    private void loadComputerArt(final ComputerDetails details) {
        final int generation = ++artGeneration;
        final int coverWidth = Math.round(56 * getResources().getDisplayMetrics().density);
        artExecutor.execute(() -> {
            List<Bitmap> covers = new ArrayList<>();
            int count = -1;
            try {
                String raw = CacheHelper.readInputStreamToString(
                        CacheHelper.openCacheFileForInput(getCacheDir(), "applist", details.uuid));
                List<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(raw));
                Set<String> hidden = getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                        .getStringSet(details.uuid, new HashSet<>());
                count = 0;
                for (NvApp app : apps) {
                    if (hidden.contains(String.valueOf(app.getAppId()))) {
                        continue;
                    }
                    count++;
                    if (covers.size() < 6) {
                        Bitmap cover = ArtHelper.loadCachedCover(this, details.uuid, app.getAppId(), coverWidth);
                        if (cover != null) {
                            covers.add(cover);
                        }
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                // No cached app list yet; the hero shows the computer without games
            }

            final int gameCount = count;
            final Bitmap backdrop = ArtHelper.blurredMosaic(covers.subList(0, Math.min(4, covers.size())));
            runOnUiThread(() -> {
                if (generation != artGeneration || isFinishing()) {
                    return;
                }
                heroGameCount = gameCount;
                ArtHelper.setBackdrop(homeBackdrop, backdrop);
                homeBackdrop.setAlpha(0f);
                homeBackdrop.animate().alpha(1f).setDuration(300).start();
                bindCovers(covers);
                for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                    ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);
                    if (computer.details.uuid.equals(details.uuid)) {
                        bindHero(computer.details);
                        break;
                    }
                }
            });
        });
    }

    private void bindCovers(List<Bitmap> covers) {
        homeCovers.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (Bitmap cover : covers) {
            ImageView view = new ImageView(this);
            view.setImageBitmap(cover);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setBackgroundResource(R.drawable.dash_pin_digit);
            view.setClipToOutline(true);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Math.round(56 * density), Math.round(84 * density));
            params.setMarginEnd(Math.round(8 * density));
            homeCovers.addView(view, params);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }

        Intent intent = getIntent();

        String hostname = intent.getStringExtra("hostname");
        int port = intent.getIntExtra("port", NvHTTP.DEFAULT_HTTP_PORT);
        pendingPairingPin = intent.getStringExtra("pin");
        pendingPairingPassphrase = intent.getStringExtra("passphrase");

        if (hostname != null && pendingPairingPin != null && pendingPairingPassphrase != null) {
            pendingPairingAddress = new ComputerDetails.AddressTuple(hostname, port);
        } else {
            pendingPairingPin = null;
            pendingPairingPassphrase = null;
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
//                        } else
                        }
                            if (pendingPairingAddress != null) {
                            if (
                                details.state == ComputerDetails.State.ONLINE &&
                                details.activeAddress.equals(pendingPairingAddress)
                            ) {
                                PcView.this.runOnUiThread(() -> {
                                    doPair(details, pendingPairingPin, pendingPairingPassphrase);
                                    pendingPairingAddress = null;
                                    pendingPairingPin = null;
                                    pendingPairingPassphrase = null;
                                });
                            }
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void refreshProfileButton() {
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
    public void onDestroy() {
        super.onDestroy();

        artExecutor.shutdownNow();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        refreshProfileButton();

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    private void showPcOptions(int position) {
        if (position < 0 || position >= pcGridAdapter.getCount()) {
            return;
        }
        final ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(position);

        String status;
        switch (computer.details.state) {
            case ONLINE:
                status = getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                status = getString(R.string.pcview_menu_header_offline);
                break;
            default:
                status = getString(R.string.pcview_menu_header_unknown);
                break;
        }
        ConsoleMenu menu = new ConsoleMenu(this, computer.details.name + " · " + status);

        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(WOL_ID, getString(R.string.pcview_menu_send_wol), R.drawable.ic_dash_moon);
            menu.add(GAMESTREAM_EOL_ID, getString(R.string.pcview_menu_eol), R.drawable.ic_dash_info);
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(PAIR_ID_OTP, getString(R.string.pcview_menu_pair_pc_otp), R.drawable.ic_dash_lock);
            menu.add(PAIR_ID, getString(R.string.pcview_menu_pair_pc), R.drawable.ic_dash_lock);
            if (computer.details.nvidiaServer) {
                menu.add(GAMESTREAM_EOL_ID, getString(R.string.pcview_menu_eol), R.drawable.ic_dash_info);
            } else {
                menu.add(OPEN_MANAGEMENT_PAGE_ID, getString(R.string.pcview_menu_open_management_page), R.drawable.ic_dash_external);
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(RESUME_ID, getString(R.string.applist_menu_resume), R.drawable.ic_dash_play);
                menu.addDestructive(QUIT_ID, getString(R.string.applist_menu_quit), R.drawable.ic_dash_power);
            }

            menu.add(FULL_APP_LIST_ID, getString(R.string.pcview_menu_app_list), R.drawable.ic_dash_grid);
            if (computer.details.nvidiaServer) {
                menu.add(GAMESTREAM_EOL_ID, getString(R.string.pcview_menu_eol), R.drawable.ic_dash_info);
            } else {
                menu.add(OPEN_MANAGEMENT_PAGE_ID, getString(R.string.pcview_menu_open_management_page), R.drawable.ic_dash_external);
            }
        }

        menu.addSeparator();
        menu.add(TEST_NETWORK_ID, getString(R.string.pcview_menu_test_network), R.drawable.ic_dash_wifi);
        menu.add(VIEW_DETAILS_ID, getString(R.string.pcview_menu_details), R.drawable.ic_dash_info);
        menu.addDestructive(DELETE_ID, getString(R.string.pcview_menu_delete_pc), R.drawable.ic_dash_trash);

        // Pause polling while the menu is open, like the old context menu did
        stopComputerUpdates(false);
        View anchor = tileRow != null ? tileRow.getFocusedItemView() : null;
        menu.show(anchor, itemId -> handlePcMenuAction(itemId, computer), this::startComputerUpdates);
    }

    private void doPair(final ComputerDetails computer, String otp, String passphrase) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        String pinStr = otp;
                        if (pinStr == null) {
                            pinStr = PairingManager.generatePinString();
                        }

                        // Spin the dialog off in a thread because it blocks
                        String pairTitle = getString(R.string.dash_pair_title, computer.name);
                        if (passphrase == null) {
                            Dialog.displayPinDialog(PcView.this, pairTitle,
                                    getString(R.string.dash_pair_message), pinStr,
                                    getString(R.string.dash_pair_help));
                        } else {
                            Dialog.displayPinDialog(PcView.this, pairTitle,
                                    getString(R.string.dash_pair_otp_message), null,
                                    getString(R.string.pair_otp_pairing_help));
                        }

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr, passphrase);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doOTPPair(final ComputerDetails computer) {
        Context context = PcView.this;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText otpInput = new EditText(context);
        otpInput.setHint("PIN");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });

        final EditText passphraseInput = new EditText(context);
        passphraseInput.setHint(getString(R.string.pair_passphrase_hint));
        passphraseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(otpInput);
        layout.addView(passphraseInput);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(R.string.pcview_menu_pair_pc_otp);
        dialogBuilder.setView(layout);

        dialogBuilder.setPositiveButton(getString(R.string.proceed), null);

        dialogBuilder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = otpInput.getText().toString();
            String passphrase = passphraseInput.getText().toString();
            if (pin.length() != 4) {
                Toast.makeText(context, getString(R.string.pair_pin_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            if (passphrase.length() < 4 ) {
                Toast.makeText(context, getString(R.string.pair_passphrase_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            doPair(computer, pin, passphrase);
            dialog.dismiss(); // Manually dismiss the dialog if the input is valid
        });
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    private boolean handlePcMenuAction(int itemId, final ComputerObject computer) {
        switch (itemId) {
            case PAIR_ID:
                doPair(computer.details, null, null);
                return true;

            case PAIR_ID_OTP:
                doOTPPair(computer.details);
                return true;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return true;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer.details, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", null, computer.details.runningGameId, false), computer.details, managerBinder, false);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details,
                                new NvApp("app", null, 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            case OPEN_MANAGEMENT_PAGE_ID:
                String managementUrl = computer.guessManagementUrl();
                if (managementUrl == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.pcview_error_no_management_url), Toast.LENGTH_LONG).show();
                } else {
                    HelpLauncher.launchUrl(PcView.this, managementUrl);
                }
                return true;

            default:
                return false;
        }
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();

                // Show the "Discovery in progress" view once the last computer is gone
                updateEmptyState();

                break;
            }
        }
    }

    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
        updateEmptyState();

        // Keep the hero in step with the focused computer's state
        if (details.uuid.equals(heroComputerUuid)) {
            bindHero(details);
        }
        else if (heroComputerUuid == null && tileRow != null) {
            onComputerFocused(tileRow.getFocusedPosition());
        }
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAdapterView(View adapterView) {
        float density = getResources().getDisplayMetrics().density;
        tileRow = (HorizontalGameListView) adapterView;
        tileRow.setItemSizes(Math.round(80 * density), Math.round(80 * density),
                Math.round(100 * density), Math.round(100 * density));
        tileRow.setTrailingView(addComputerTile);
        tileRow.setOnItemFocusListener((view, position) -> onComputerFocused(position));
        tileRow.setAdapter(pcGridAdapter);
        tileRow.setOnItemClickListener((view, position, id) -> onComputerSelected(position));
        tileRow.setOnItemLongClickListener((view, position, id) -> {
            showPcOptions(position);
            return true;
        });
        if (pcGridAdapter.getCount() == 0) {
            addComputerTile.requestFocus();
        }
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        // Computers are always a single row of tiles
    }

    private void onComputerSelected(int position) {
        ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(position);
        if (computer.details.state == ComputerDetails.State.UNKNOWN ||
            computer.details.state == ComputerDetails.State.OFFLINE) {
            // Open the options if a PC is offline or refreshing
            showPcOptions(position);
        } else if (computer.details.pairState != PairState.PAIRED) {
            // Pair an unpaired machine by default
            doPair(computer.details, null, null);
        } else {
            doAppList(computer.details, false, false);
        }
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
        public String guessManagementUrl() {
            if (details.activeAddress == null) return null;
            return "https://" + details.activeAddress.address + ":" + (details.guessExternalPort() + 1);
        }
    }
}
