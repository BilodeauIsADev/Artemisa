package com.limelight.preferences;

import static com.limelight.utils.ServerHelper.getActiveDisplay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.preference.TwoStatePreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;
import com.limelight.BuildConfig;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.PerformanceDataTracker;
import com.limelight.utils.UiHelper;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class StreamSettings extends AppCompatActivity {
    private static final int TAB_STREAM = 0;
    private static final int TAB_AUDIO = 1;
    private static final int TAB_CONTROLS = 2;
    private static final int TAB_OVERLAY = 3;
    private static final int TAB_APPEARANCE = 4;
    private static final int TAB_SYSTEM = 5;
    private static final int TAB_COUNT = 6;

    private int currentTab = TAB_STREAM;
    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    private SettingsFragment prefsFragment;
    private TextView[] tabViews;
    private View listContainer;
    private TextView guideSection;
    private TextView guideTitle;
    private TextView guideDescription;
    private View guideOptionsScroll;
    private LinearLayout guideOptions;
    private TextView hintSelect;
    private TextView hintBack;
    private View[] hintAdjust;
    // The list setting whose options are open in the guide card, or null
    private ListPreference openList;
    private boolean settingsShown;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        // The new list starts on the row that had focus, or on the first row the first time
        Preference focused = prefsFragment != null ? prefsFragment.getPreferenceFor(getWindow().getDecorView().findFocus()) : null;
        prefsFragment = new SettingsFragment(PreferenceConfiguration.readPreferences(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        ), currentTab);
        if (focused != null || !settingsShown) {
            prefsFragment.focusOnLoad(focused != null ? focused.getKey() : null);
        }
        settingsShown = true;
        getSupportFragmentManager().beginTransaction().replace(
                R.id.stream_settings, prefsFragment
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);
        setupCategoryTabs();

//        UiHelper.notifyNewRootView(this);
    }

    private void setupCategoryTabs() {
        tabViews = new TextView[]{
                findViewById(R.id.settingsTabStream),
                findViewById(R.id.settingsTabAudio),
                findViewById(R.id.settingsTabControls),
                findViewById(R.id.settingsTabOverlay),
                findViewById(R.id.settingsTabAppearance),
                findViewById(R.id.settingsTabSystem)
        };
        listContainer = findViewById(R.id.stream_settings);
        guideSection = findViewById(R.id.settingsGuideSection);
        guideTitle = findViewById(R.id.settingsGuideTitle);
        guideDescription = findViewById(R.id.settingsGuideDescription);
        guideOptionsScroll = findViewById(R.id.settingsGuideOptionsScroll);
        guideOptions = findViewById(R.id.settingsGuideOptions);
        hintSelect = findViewById(R.id.settingsHintSelect);
        hintBack = findViewById(R.id.settingsHintBack);
        hintAdjust = new View[]{
                findViewById(R.id.settingsHintAdjustLeft),
                findViewById(R.id.settingsHintAdjustRight),
                findViewById(R.id.settingsHintAdjust)
        };

        for (int i = 0; i < tabViews.length; i++) {
            final int tab = i;
            tabViews[i].setOnClickListener(view -> selectCategoryTab(tab, true));
        }
        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalFocusChangeListener(this::onGlobalFocusChanged);
        updateCategoryTabs();
        showGuide(null);
    }

    private int tabIndexOf(View view) {
        for (int i = 0; tabViews != null && i < tabViews.length; i++) {
            if (tabViews[i] == view) {
                return i;
            }
        }
        return -1;
    }

    private void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (openList != null || prefsFragment == null) {
            return;
        }

        int tab = tabIndexOf(newFocus);
        if (tab >= 0) {
            if (tab != currentTab && prefsFragment.getPreferenceFor(oldFocus) != null) {
                // Up from the list lands on the selected section, not whichever tab is nearest
                tabViews[currentTab].requestFocus();
            }
            else if (tab != currentTab) {
                // Moving along the tabs shows each section, like Android TV settings
                selectCategoryTab(tab, false);
            }
            showGuide(null);
            return;
        }

        Preference preference = prefsFragment.getPreferenceFor(newFocus);
        if (preference != null) {
            showGuide(preference);
        }
    }

    // The guide card explains the focused row, or names the section when a tab has focus.
    void showGuide(Preference preference) {
        if (guideTitle == null) {
            return;
        }
        guideSection.setText(tabViews[currentTab].getText());
        if (preference == null) {
            guideSection.setVisibility(View.GONE);
            guideTitle.setText(tabViews[currentTab].getText());
            guideDescription.setText(null);
            return;
        }
        guideSection.setVisibility(View.VISIBLE);
        guideTitle.setText(preference.getTitle());
        guideDescription.setText(prefsFragment.getDescription(preference));
    }

    // Shows a list setting's choices in the guide card instead of a dialog.
    void showOptions(ListPreference preference) {
        openList = preference;
        showGuide(preference);
        guideDescription.setMaxLines(4);
        guideOptions.removeAllViews();

        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        int selected = preference.findIndexOfValue(preference.getValue());
        LayoutInflater inflater = LayoutInflater.from(this);
        View focusTarget = null;
        for (int i = 0; i < entries.length; i++) {
            TextView option = (TextView) inflater.inflate(R.layout.dash_settings_option, guideOptions, false);
            option.setId(View.generateViewId());
            option.setText(entries[i]);
            boolean isSelected = i == selected;
            option.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    isSelected ? R.drawable.dash_radio_on : R.drawable.dash_radio_off, 0, 0, 0);
            // Focus stays inside the card until a choice is made or cancelled
            option.setNextFocusLeftId(option.getId());
            option.setNextFocusRightId(option.getId());

            final String value = values[i].toString();
            option.setOnClickListener(v -> {
                closeOptions();
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                }
            });
            guideOptions.addView(option);
            if (focusTarget == null || isSelected) {
                focusTarget = option;
            }
        }
        if (guideOptions.getChildCount() > 0) {
            View first = guideOptions.getChildAt(0);
            View last = guideOptions.getChildAt(guideOptions.getChildCount() - 1);
            first.setNextFocusUpId(first.getId());
            last.setNextFocusDownId(last.getId());
        }

        guideOptionsScroll.setVisibility(View.VISIBLE);
        listContainer.setAlpha(0.5f);
        hintSelect.setText(R.string.dash_choose);
        hintBack.setText(R.string.dash_cancel);
        setAdjustHintVisible(false);
        if (focusTarget != null) {
            focusTarget.requestFocus();
        }
    }

    private void closeOptions() {
        ListPreference preference = openList;
        openList = null;
        guideOptionsScroll.setVisibility(View.GONE);
        guideOptions.removeAllViews();
        guideDescription.setMaxLines(9);
        listContainer.setAlpha(1f);
        hintSelect.setText(R.string.dash_select);
        hintBack.setText(R.string.dash_back);
        setAdjustHintVisible(true);
        if (preference != null && prefsFragment != null) {
            prefsFragment.focusRow(preference.getKey());
        }
    }

    private void setAdjustHintVisible(boolean visible) {
        for (View view : hintAdjust) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void selectCategoryTab(int tab, boolean focusList) {
        currentTab = (tab + TAB_COUNT) % TAB_COUNT;
        updateCategoryTabs();
        if (prefsFragment != null) {
            prefsFragment.setCategoryTab(currentTab);
            if (focusList) {
                prefsFragment.focusRow(null);
            }
        }
        showGuide(null);
    }

    private void updateCategoryTabs() {
        if (tabViews == null) {
            return;
        }
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setSelected(i == currentTab);
        }
    }

    // Left and right change the focused row's value in place; rows never move focus sideways.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN && openList == null && prefsFragment != null &&
                (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            Preference preference = prefsFragment.getPreferenceFor(getWindow().getDecorView().findFocus());
            if (preference != null) {
                // Holding the button speeds up sliders such as bitrate
                int multiplier = event.getRepeatCount() >= 8 ? 10 : 1;
                prefsFragment.adjust(preference, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, multiplier);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (openList == null) {
                // Stay in the list if that's where focus was, otherwise move along the tabs
                boolean inList = prefsFragment != null && prefsFragment.getPreferenceFor(getWindow().getDecorView().findFocus()) != null;
                selectCategoryTab(currentTab + (keyCode == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1), inList);
                if (!inList) {
                    tabViews[currentTab].requestFocus();
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            onBackPressed();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        if (openList != null) {
            closeOptions();
            return;
        }
        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
        if (!newPrefs.language.equals(previousPrefs.language)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            } else {
                if (newPrefs.language == PreferenceConfiguration.DEFAULT_LANGUAGE) {
                    Toast.makeText(this, "Language has been reset to default, please restart the app!", Toast.LENGTH_LONG).show();
                    System.exit(0);
                }
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final int TAB_ALL = -1;
        private static final String LANGUAGE_PREF_KEY = "list_languages";
        private static final String[][] CATEGORY_KEYS = {
                {
                        "category_video_settings"
                },
                {
                        "category_audio_settings",
                        "category_host_settings"
                },
                {
                        "category_gamepad_settings",
                        "category_input_settings"
                },
                {
                        "category_perf_monitor_settings"
                },
                {
                        "category_appearance_settings"
                },
                {
                        "category_advanced_settings",
                        "category_settings_misc"
                }
        };

        // Row descriptions, shown in the guide card; the rows themselves only show values
        private final Map<String, CharSequence> descriptions = new HashMap<>();
        // Rows by the view id each one is bound to, to find the row behind a focused view
        private final SparseArray<Preference> rowsById = new SparseArray<>();
        private boolean focusOnLoad;
        private String focusOnLoadKey;

        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;
        private int categoryTab = TAB_ALL;

        private PreferenceConfiguration prevPrefConfig;

        public SettingsFragment(PreferenceConfiguration prefCfg) {
            prevPrefConfig = prefCfg;
        }

        public SettingsFragment(PreferenceConfiguration prefCfg, int categoryTab) {
            this(prefCfg);
            this.categoryTab = categoryTab;
        }

        protected SharedPreferences getPrefs() {
            return getPreferenceManager().getSharedPreferences();
        }

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait, boolean is_custom) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean is_custom) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true, is_custom);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false, is_custom);
        }

        private void addNativeFrameRateEntry(float framerate, boolean is_custom) {
            if (!is_custom) {
                framerate = Math.round(framerate);
                if (framerate == 0) {
                    return;
                }
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = is_custom ? Float.toString(framerate) : Integer.toString(Math.round(framerate));
            String fpsName = (is_custom ? getResources().getString(R.string.resolution_prefix_custom) : getResources().getString(R.string.resolution_prefix_native)) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(res, fps))
                    .apply();
        }

        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            // One grouped panel: rows separated by hairlines rather than floating cards
            setDivider(new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.dash_line)));
            setDividerHeight(Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
            return view;
        }

        @NonNull
        @Override
        public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater,
                                                 @NonNull ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView list = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            list.setVerticalScrollBarEnabled(false);
            // Rebinding a row in place keeps focus on it when its value changes from the D-pad
            if (list.getItemAnimator() instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
            }
            return list;
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState, boolean unused) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            initializePreferences();
            moveDescriptionsToGuide(getPreferenceScreen());
            applyCategoryFilter();
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (focusOnLoad) {
                focusRow(focusOnLoadKey);
            }
        }

        /** Focus the row with this key (or the first row when null) once the list is shown. */
        void focusOnLoad(String key) {
            focusOnLoad = true;
            focusOnLoadKey = key;
        }

        CharSequence getDescription(Preference preference) {
            return descriptions.get(preference.getKey());
        }

        // Before list values replace their summaries, keep the XML descriptions for the guide.
        private void collectDescriptions(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    collectDescriptions((PreferenceGroup) preference);
                    continue;
                }
                CharSequence description = preference instanceof SeekBarPreference ?
                        ((SeekBarPreference) preference).getDescription() : preference.getSummary();
                if (preference.getKey() != null && !TextUtils.isEmpty(description)) {
                    descriptions.put(preference.getKey(), description);
                }
            }
        }

        // Rows other than lists and sliders have no value to show, so their summary (which may
        // have been updated since, such as a firmware note on HDR) moves to the guide.
        private void moveDescriptionsToGuide(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    moveDescriptionsToGuide((PreferenceGroup) preference);
                    continue;
                }
                int viewId = View.generateViewId();
                preference.setViewId(viewId);
                rowsById.put(viewId, preference);

                if (preference instanceof ListPreference || preference instanceof SeekBarPreference) {
                    continue;
                }
                CharSequence summary = preference.getSummary();
                if (preference.getKey() != null && !TextUtils.isEmpty(summary)) {
                    descriptions.put(preference.getKey(), summary);
                }
                preference.setSummary(null);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setSummaryOn(null);
                    ((TwoStatePreference) preference).setSummaryOff(null);
                }
            }
        }

        /** The row containing this view, or null when it isn't in the list. */
        Preference getPreferenceFor(View view) {
            RecyclerView list = getListView();
            if (list == null || view == null) {
                return null;
            }
            View row = list.findContainingItemView(view);
            return row != null ? rowsById.get(row.getId()) : null;
        }

        /** Focus the row with this key, or the first row when null, once it has been laid out. */
        void focusRow(String key) {
            focusRow(key, 10);
        }

        private void focusRow(String key, int attempts) {
            RecyclerView list = getListView();
            if (list == null) {
                return;
            }
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                Preference preference = rowsById.get(row.getId());
                if (preference != null && row.isFocusable() &&
                        (key == null ? preference.isSelectable() : key.equals(preference.getKey()))) {
                    row.requestFocus();
                    return;
                }
            }
            if (key != null) {
                scrollToPreference(key);
            }
            else {
                list.scrollToPosition(0);
            }
            if (attempts > 0) {
                list.postDelayed(() -> focusRow(key, attempts - 1), 32);
            }
        }

        /** Steps a row's value left or right: lists move one entry, sliders one step, switches off/on. */
        void adjust(Preference preference, int direction, int multiplier) {
            if (preference instanceof ListPreference &&
                    !LANGUAGE_PREF_KEY.equals(preference.getKey())) {
                // Language changes restart the UI, so it is only changed from its option list
                ListPreference list = (ListPreference) preference;
                CharSequence[] values = list.getEntryValues();
                int target = list.findIndexOfValue(list.getValue()) + direction;
                if (target >= 0 && target < values.length) {
                    String value = values[target].toString();
                    if (list.callChangeListener(value)) {
                        list.setValue(value);
                    }
                }
            }
            else if (preference instanceof SeekBarPreference) {
                ((SeekBarPreference) preference).step(direction, multiplier);
            }
            else if (preference instanceof TwoStatePreference) {
                TwoStatePreference toggle = (TwoStatePreference) preference;
                boolean checked = direction > 0;
                if (toggle.isChecked() != checked && toggle.callChangeListener(checked)) {
                    toggle.setChecked(checked);
                }
            }
        }

        public void setCategoryTab(int categoryTab) {
            this.categoryTab = categoryTab;
            applyCategoryFilter();
        }

        private void applyCategoryFilter() {
            if (getPreferenceScreen() == null || categoryTab == TAB_ALL) {
                return;
            }

            for (int tab = 0; tab < CATEGORY_KEYS.length; tab++) {
                boolean visible = tab == categoryTab;
                for (String key : CATEGORY_KEYS[tab]) {
                    Preference category = findPreference(key);
                    if (category != null) {
                        category.setVisible(visible);
                        // A section with one category already shows its name above the list
                        if (CATEGORY_KEYS[tab].length == 1) {
                            category.setLayoutResource(R.layout.dash_preference_category_hidden);
                        }
                    }
                }
            }
        }

        public void initializePreferences() {
            addPreferencesFromResource(R.xml.preferences);
            collectDescriptions(getPreferenceScreen());
            showListValuesAsSummaries(getPreferenceScreen());

            AppCompatActivity activity = (AppCompatActivity) requireActivity();
            PackageManager pm = activity.getPackageManager();

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide USB driver options on devices without USB host support
            if (!pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Check custom resolution
            String customResStr = prevPrefConfig.customResolution;
            if(customResStr != null && !customResStr.isEmpty()){
                String[] resolutionSegments = customResStr.split("x");
                if(resolutionSegments.length == 2){
                    try {
                        addNativeResolutionEntries(Integer.parseInt(resolutionSegments[0]), Integer.parseInt(resolutionSegments[1]), false, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Check custom refresh rate
            String customRefreshRateStr = prevPrefConfig.customRefreshRate;
            if (customRefreshRateStr != null && !customRefreshRateStr.isEmpty()) {
                try {
                    float customRefreshRateValue = Float.parseFloat(customRefreshRateStr);
                    if (customRefreshRateValue > 0) {
                        addNativeFrameRateEntry(customRefreshRateValue, true);
                    }
                } catch (NumberFormatException e) {
                    getPrefs().edit().remove(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING).apply();
                }
            }

            Display display = activity.getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets, false);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(requireContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, PreferenceConfiguration.RES_1440P);
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, PreferenceConfiguration.RES_1080P);
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, PreferenceConfiguration.RES_720P);
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false, false);
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "120", "90");
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "90", "60");
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps, false);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    reloadSettings();

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_video_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();
                Log.d("HDR CAP", display + "");
                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    TwoStatePreference hdrPref = category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            findPreference("checkbox_enable_perf_logging").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Boolean loggingEnabled = (Boolean) newValue;

                    if(!loggingEnabled) {
                        new PerformanceDataTracker().clearLogs(preference.getContext());
                    }

                    // Allow the original preference change to take place
                    return true;
                }
            });

            Preference _pref;
            _pref = findPreference("import_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("import_special_button_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_SPECIAL_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("share_performance_logs");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Context context = preference.getContext();
                        PerformanceDataTracker tracker = new PerformanceDataTracker();
                        String logs = tracker.getLog(context);

                        if (logs == null || logs.trim().isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.toast_no_logs), Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        String prefixMessage = context.getString(R.string.email_prefix_message);
                        String emailRecipient = context.getString(R.string.email_recipient);
                        String emailSubject = context.getString(R.string.email_subject);
                        String chooserTitle = context.getString(R.string.email_chooser_title);
                        String noEmailClientsMsg = context.getString(R.string.toast_no_email_clients);

                        try {
                            File cacheDir = context.getCacheDir();
                            File logFile = new File(cacheDir, "artemistics_logs.txt");
                            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                                fos.write(logs.getBytes(StandardCharsets.UTF_8));
                            }

                            Uri logFileUri = FileProvider.getUriForFile(context,
                                    context.getPackageName() + ".fileprovider",
                                    logFile);

                            Intent emailIntent = new Intent(Intent.ACTION_SEND);
                            emailIntent.setType("text/plain");
                            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailRecipient});
                            emailIntent.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
                            emailIntent.putExtra(Intent.EXTRA_TEXT, prefixMessage);
                            emailIntent.putExtra(Intent.EXTRA_STREAM, logFileUri);

                            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            context.startActivity(Intent.createChooser(emailIntent, chooserTitle));
                        } catch (IOException e) {
                            Log.d("PerformanceDataTracker", "Error creating log file");
                        } catch (android.content.ActivityNotFoundException ex) {
                            Toast.makeText(context, noEmailClientsMsg, Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }
                });
            }

            _pref = findPreference("export_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        File file = new File(requireActivity().getExternalCacheDir(),"export_settings");
                        if(!file.exists()){
                            file.mkdir();
                        }
                        File file1= getJsonContent(requireActivity(),file);
                        if(file1==null){
                            Toast.makeText(requireActivity(),getString(R.string.pref_error_occurred),Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        Uri uri;
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        String authority= BuildConfig.APPLICATION_ID+".fileprovider";
                        uri = FileProvider.getUriForFile(requireActivity(),authority,file1);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setType("application/json");
                        startActivity(Intent.createChooser(intent,getString(R.string.pref_save_keyboard_profile)));
                        return false;
                    }
                });
            }

            _pref = findPreference("pref_debug_info");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        Intent intent=new Intent(requireActivity(), DebugInfoActivity.class);
                        requireActivity().startActivity(intent);
                        return false;
                    }
                });
            }
        }

        // On a TV the current value is more useful under a list setting than its description
        private void showListValuesAsSummaries(androidx.preference.PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                if (preference instanceof androidx.preference.PreferenceGroup) {
                    showListValuesAsSummaries((androidx.preference.PreferenceGroup) preference);
                }
                else if (preference instanceof ListPreference) {
                    preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                }
            }
        }

        private void removeEntryFromListAndSetValue(String resolutionPrefString, String entryToRemove, String nextDefault) {
            removeValue(resolutionPrefString, entryToRemove, new Runnable() {
                @Override
                public void run() {
                    SharedPreferences prefs = getPrefs();
                    setValue(resolutionPrefString, nextDefault);
                    resetBitrateToDefault(prefs, null, null);
                }
            });
        }

        protected void reloadSettings() {
            // HACK: We need to let the preference change succeed before reinitializing to ensure
            // it's reflected in the new layout.
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Ensure the activity is still open when this timeout expires
                    StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                    if (settingsActivity != null) {
                        settingsActivity.reloadSettings();
                    }
                }
            }, 500);
        }

        int READ_REQUEST_CODE = 1001;
        int READ_REQUEST_SPECIAL_CODE = 1002;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                    SharedPreferences.Editor prefEditor = requireActivity().getSharedPreferences(name, Activity.MODE_PRIVATE).edit();
                    JSONObject object = new JSONObject(json);
                    Iterator it = object.keys();
                    prefEditor.clear();
                    while (it.hasNext()) {
                        String key = (String) it.next();// 获得key
                        String value = object.getString(key);// 获得value
                        prefEditor.putString(key, value);
                    }
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (requestCode == READ_REQUEST_SPECIAL_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor prefEditor = getActivity().getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit();
                    prefEditor.putString(GameMenu.KEY_NAME, json);
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof ListPreference) {
                ((StreamSettings) requireActivity()).showOptions((ListPreference) preference);
            } else if (preference instanceof ConfirmDeleteOscPreference) {
                DialogFragment dialogFragment = ConfirmDeleteOscPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else if (preference instanceof ConfirmDeleteKeyboardPreference) {
                DialogFragment dialogFragment = ConfirmDeleteKeyboardPreference.DialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else super.onDisplayPreferenceDialog(preference);
        }

        private File getJsonContent(Context context,File file){
            String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
            SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
            Map<String,?> map = pref.getAll();
            File file1= new File(file,name+".json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }

        //获取所有设置项配置文件
        private File getAllJsonData(File file){
            SharedPreferences pref = getPrefs();
            Map<String,?> map = pref.getAll();
            //获取适配电脑的数据库信息
//            List<ComputerDetails> map= new ComputerDatabaseManager(context).getAllComputers();
            File file1= new File(file,"allJSON.json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }
    }
}

