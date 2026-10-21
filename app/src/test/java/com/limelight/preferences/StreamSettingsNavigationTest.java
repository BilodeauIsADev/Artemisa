package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.TestLogSuppressor;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

@Config(sdk = {33}, qualifiers = "w960dp-h540dp-land-xhdpi",
        shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class StreamSettingsNavigationTest {
    private StreamSettings activity;

    @BeforeClass
    public static void silenceLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(StreamSettings.class).setup().get();
        // Leave touch mode as a controller does; the list then focuses its first row
        activity.findViewById(R.id.settingsTabStream).requestFocusFromTouch();
        settle();
    }

    private void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private View focused() {
        return activity.getWindow().getDecorView().findFocus();
    }

    private String focusedTitle() {
        TextView title = focused().findViewById(android.R.id.title);
        return title != null ? title.getText().toString() : ((TextView) focused()).getText().toString();
    }

    private String focusedValue() {
        return ((TextView) focused().findViewById(android.R.id.summary)).getText().toString();
    }

    private String guideTitle() {
        return ((TextView) activity.findViewById(R.id.settingsGuideTitle)).getText().toString();
    }

    private void move(int direction) {
        focused().focusSearch(direction).requestFocus();
        settle();
    }

    private void press(int keyCode) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        settle();
    }

    private void moveToRow(String title) {
        for (int i = 0; i < 10 && !focusedTitle().equals(title); i++) {
            move(View.FOCUS_DOWN);
        }
        assertEquals(title, focusedTitle());
    }

    @Test
    public void startsOnTheFirstRowWithItsGuide() {
        assertEquals("Resolution", focusedTitle());
        assertEquals("Resolution", guideTitle());
    }

    @Test
    public void rightRaisesTheBitrateInPlace() {
        moveToRow("Bitrate");
        String before = focusedValue();

        press(KeyEvent.KEYCODE_DPAD_RIGHT);

        assertEquals("Bitrate", focusedTitle());
        assertTrue(!before.equals(focusedValue()));
    }

    @Test
    public void listOptionsOpenInTheGuideAndBackReturnsToTheRow() {
        moveToRow("Frame pacing");

        focused().performClick();
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.settingsGuideOptionsScroll).getVisibility());
        assertTrue(((View) focused().getParent()).getId() == R.id.settingsGuideOptions);
        // The focused mode explains itself in the guide card
        String description = ((TextView) activity.findViewById(R.id.settingsGuideDescription)).getText().toString();
        assertTrue(java.util.Arrays.asList(activity.getResources()
                .getStringArray(R.array.video_frame_pacing_summaries)).contains(description));

        activity.onBackPressed();
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.settingsGuideOptionsScroll).getVisibility());
        assertEquals("Frame pacing", focusedTitle());
    }

    @Test
    public void upFromTheListLandsOnTheSelectedSection() {
        activity.onKeyUp(KeyEvent.KEYCODE_BUTTON_R1, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R1));
        settle();
        move(View.FOCUS_DOWN);

        for (int i = 0; i < 6 && focused().getId() != R.id.settingsTabAudio; i++) {
            View next = focused().focusSearch(View.FOCUS_UP);
            if (next == null) {
                break;
            }
            next.requestFocus();
            settle();
        }
        assertEquals(R.id.settingsTabAudio, focused().getId());
        assertTrue(activity.findViewById(R.id.settingsTabAudio).isSelected());
    }
}
