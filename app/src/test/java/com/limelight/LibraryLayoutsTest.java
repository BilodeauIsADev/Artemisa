package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.GameManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AbsListView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.utils.CacheHelper;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.OutputStream;
import java.time.Duration;

@Config(sdk = {33}, qualifiers = "w960dp-h540dp-land-xhdpi",
        shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class LibraryLayoutsTest {
    private static final String UUID = "test-uuid";
    private static final String[][] APPS = {
            {"1", "A Quiet Place: The Road Ahead"},
            {"2", "Desktop"},
            {"3", "Fatal Frame II: Crimson Butterfly Remake"},
            {"4", "Honkai: Star Rail"},
            {"5", "Outer Wilds"},
    };

    @BeforeClass
    public static void silenceLogs() {
        TestLogSuppressor.install();
    }

    private void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private void prepare(String layout) throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences("GlPreferences", 0).edit()
                .putString("Renderer", "TestRenderer").putString("Fingerprint", Build.FINGERPRINT).commit();
        shadowOf((android.app.Application) ctx).setSystemService(Context.GAME_SERVICE, mock(GameManager.class));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putString("list_library_layout", layout)
                .putString("list_resolution", "3840x2160").putString("list_fps", "60")
                .putInt("seekbar_bitrate_kbps", 80000).putBoolean("checkbox_enable_hdr", true).commit();

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><root status_code=\"200\">");
        for (int i = 0; i < APPS.length; i++) {
            xml.append("<App><IDX>").append(i).append("</IDX><AppTitle>").append(APPS[i][1].replace("&", "&amp;"))
                    .append("</AppTitle><ID>").append(APPS[i][0]).append("</ID><IsHdrSupported>1</IsHdrSupported></App>");
        }
        xml.append("</root>");
        try (OutputStream out = CacheHelper.openCacheFileForOutput(ctx.getCacheDir(), "applist", UUID)) {
            out.write(xml.toString().getBytes("UTF-8"));
        }

        ComputerDetails details = new ComputerDetails();
        details.uuid = UUID;
        details.name = "VaporZPC";
        details.state = ComputerDetails.State.ONLINE;
        ComputerManagerService.ComputerManagerBinder binder =
                mock(ComputerManagerService.ComputerManagerBinder.class, RETURNS_DEEP_STUBS);
        when(binder.getComputer(anyString())).thenReturn(details);
        when(binder.getUniqueId()).thenReturn("0123456789ABCDEF");
        shadowOf((android.app.Application) ctx).setComponentNameAndServiceForBindService(
                new ComponentName(ctx, ComputerManagerService.class), binder);
    }

    private AppView launch() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), AppView.class);
        intent.putExtra("UUID", UUID);
        intent.putExtra("Name", "VaporZPC");
        AppView a = Robolectric.buildActivity(AppView.class, intent).setup().get();
        settle();
        a.findViewById(R.id.settingsButton).requestFocusFromTouch();
        settle();
        return a;
    }

    private View focused(AppView a) {
        return a.getWindow().getDecorView().findFocus();
    }

    private void moveDown(AppView a) {
        View next = focused(a).focusSearch(View.FOCUS_DOWN);
        if (next != null) next.requestFocus();
        settle();
    }

    @Test
    public void gridOpensTheGamePageAndBackClosesIt() throws Exception {
        prepare("grid");
        AppView a = launch();
        moveDown(a);
        AbsListView grid = a.findViewById(R.id.fragmentView);
        assertEquals(5, grid.getCount());
        assertEquals(View.GONE, a.findViewById(R.id.gameDetails).getVisibility());

        grid.setSelection(2);
        settle();
        grid.performItemClick(grid.getSelectedView(), 2, 2);
        settle();
        assertEquals(View.VISIBLE, a.findViewById(R.id.gameDetails).getVisibility());
        assertEquals(R.id.libraryPlayButton, focused(a).getId());
        assertEquals("Fatal Frame II: Crimson Butterfly Remake",
                ((TextView) a.findViewById(R.id.libraryTitle)).getText().toString());

        a.onBackPressed();
        settle();
        assertEquals(View.GONE, a.findViewById(R.id.gameDetails).getVisibility());
        assertFalse(a.isFinishing());
        assertEquals(R.id.fragmentView, focused(a).getId());
    }

    @Test
    public void showcaseShowsTheFocusedGameBesideItsStrip() throws Exception {
        prepare("showcase");
        AppView a = launch();
        assertTrue(a.findViewById(R.id.fragmentView) instanceof com.limelight.ui.SpotlightCarouselView);
        assertEquals("A Quiet Place: The Road Ahead",
                ((TextView) a.findViewById(R.id.libraryTitle)).getText().toString());
    }

    @Test
    public void titleListListsEveryGame() throws Exception {
        prepare("list");
        AppView a = launch();
        AbsListView list = a.findViewById(R.id.fragmentView);
        assertEquals(5, list.getCount());
        assertTrue(a.findViewById(R.id.libraryPlayButton) == null);
    }
}
