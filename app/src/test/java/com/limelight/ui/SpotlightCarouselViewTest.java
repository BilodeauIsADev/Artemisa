package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.limelight.TestLogSuppressor;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

import static org.robolectric.Shadows.shadowOf;

@Config(sdk = {33}, qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner.class)
public class SpotlightCarouselViewTest {
    private SpotlightCarouselView carousel;
    private int focusedEvents;

    @BeforeClass
    public static void silenceLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        carousel = new SpotlightCarouselView(activity);
        carousel.setItemSize(100, 140);
        carousel.setOnItemFocusListener((view, position) -> focusedEvents++);
        activity.setContentView(carousel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        carousel.setAdapter(new ItemsAdapter(5));
        settle();
    }

    private void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    private void press(int keyCode) {
        carousel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        carousel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        settle();
    }

    @Test
    public void firstItemTakesFocusAndSitsInTheCentre() {
        View first = carousel.getChildAt(0);
        assertTrue(first.isFocused());
        assertEquals(0, carousel.getFocusedPosition());
        assertEquals(0f, first.getTranslationX(), 0.01f);
        assertEquals(1f, first.getScaleX(), 0.01f);
        assertTrue(focusedEvents > 0);
    }

    @Test
    public void rightSlidesTheNextItemIntoTheCentre() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT);

        assertEquals(1, carousel.getFocusedPosition());
        assertTrue(carousel.getChildAt(1).isFocused());
        assertEquals(0f, carousel.getChildAt(1).getTranslationX(), 0.01f);
        assertEquals(1f, carousel.getChildAt(1).getScaleX(), 0.01f);

        // Neighbours shrink and spread out on both sides; far items are hidden
        View left = carousel.getChildAt(0);
        View right = carousel.getChildAt(2);
        assertTrue(left.getTranslationX() < 0);
        assertTrue(right.getTranslationX() > 0);
        assertEquals(0.75f, left.getScaleX(), 0.01f);
        assertEquals(0.625f, carousel.getChildAt(3).getScaleX(), 0.01f);
        assertEquals(0f, carousel.getChildAt(4).getAlpha(), 0.01f);
        assertEquals(-left.getTranslationX(), right.getTranslationX(), 0.5f);
    }

    @Test
    public void leftAtTheFirstItemStaysPut() {
        press(KeyEvent.KEYCODE_DPAD_LEFT);

        assertEquals(0, carousel.getFocusedPosition());
        assertTrue(carousel.getChildAt(0).isFocused());
    }

    @Test
    public void onlyTheCurrentItemIsReachableByFocusSearch() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);

        java.util.ArrayList<View> focusables = new java.util.ArrayList<>();
        carousel.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
        assertEquals(1, focusables.size());
        assertEquals(carousel.getChildAt(2), focusables.get(0));
    }

    @Test
    public void trailingViewIsTheLastStop() {
        View trailing = new View(carousel.getContext());
        trailing.setFocusable(true);
        trailing.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        carousel.setTrailingView(trailing);
        settle();

        for (int i = 0; i < 7; i++) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        assertTrue(trailing.isFocused());
        assertEquals(5, carousel.getFocusedPosition());
        // The last adapter item is still the one the options menu acts on
        assertEquals(carousel.getChildAt(4), carousel.getFocusedItemView());
    }

    private static class ItemsAdapter extends BaseAdapter {
        private final int count;

        ItemsAdapter(int count) {
            this.count = count;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public Object getItem(int position) {
            return "Game " + position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return new View(parent.getContext());
        }
    }
}
