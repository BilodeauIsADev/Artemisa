package com.limelight.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.limelight.grid.assets.DiskAssetLoader;

import java.io.File;
import java.util.List;

/**
 * Box art helpers for the art-led dashboard: cached cover loading, a cheap blur for
 * full-screen backdrops (RenderEffect needs API 31; the Shield runs API 30), and the
 * dimmed, saturated colour treatment the backdrops share.
 */
public final class ArtHelper {
    private static final int BLUR_WIDTH = 48;

    private ArtHelper() {
    }

    /** Loads a cached cover from disk, sampled down to roughly maxWidth. Call off the UI thread. */
    public static Bitmap loadCachedCover(Context context, String computerUuid, int appId, int maxWidth) {
        File file = new DiskAssetLoader(context).getFile(computerUuid, appId);
        if (!file.exists()) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Integer.highestOneBit(bounds.outWidth / Math.max(1, maxWidth)));
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    /** Returns the bitmap an ImageView is currently showing, or null. */
    public static Bitmap bitmapOf(ImageView view) {
        if (view == null) {
            return null;
        }
        Drawable drawable = view.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        return null;
    }

    /** A small, heavily blurred copy of the source, meant to be scaled up to full screen. */
    public static Bitmap blur(Bitmap source) {
        if (source == null || source.getWidth() == 0 || source.getHeight() == 0) {
            return null;
        }
        int height = Math.max(1, Math.round(BLUR_WIDTH * source.getHeight() / (float) source.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(source, BLUR_WIDTH, height, true)
                .copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(small, 4);
        return small;
    }

    /** Draws the covers side by side, then blurs them into one backdrop. */
    public static Bitmap blurredMosaic(List<Bitmap> covers) {
        if (covers == null || covers.isEmpty()) {
            return null;
        }
        int tileWidth = 60;
        int tileHeight = 90;
        Bitmap mosaic = Bitmap.createBitmap(tileWidth * covers.size(), tileHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mosaic);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < covers.size(); i++) {
            Bitmap cover = covers.get(i);
            canvas.drawBitmap(cover, null,
                    new Rect(i * tileWidth, 0, (i + 1) * tileWidth, tileHeight), paint);
        }
        Bitmap blurred = blur(mosaic);
        mosaic.recycle();
        return blurred;
    }

    /** Backdrop treatment: half brightness, a little extra saturation. */
    public static ColorMatrixColorFilter backdropFilter(float brightness) {
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(1.3f);
        ColorMatrix scale = new ColorMatrix();
        scale.setScale(brightness, brightness, brightness, 1f);
        saturation.postConcat(scale);
        return new ColorMatrixColorFilter(saturation);
    }

    /** Sets a blurred bitmap on a backdrop view so the upscale is filtered, not blocky. */
    public static void setBackdrop(ImageView view, Bitmap blurred) {
        if (blurred == null) {
            view.setImageDrawable(null);
            return;
        }
        BitmapDrawable drawable = new BitmapDrawable(view.getResources(), blurred);
        drawable.setFilterBitmap(true);
        view.setImageDrawable(drawable);
    }

    private static void boxBlur(Bitmap bitmap, int radius) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        int[] scratch = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            blurLine(pixels, scratch, w, h, radius, true);
            blurLine(scratch, pixels, w, h, radius, false);
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void blurLine(int[] in, int[] out, int w, int h, int radius, boolean horizontal) {
        int lines = horizontal ? h : w;
        int length = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.min(length - 1, Math.max(0, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                    count++;
                }
                int index = horizontal ? line * w + i : i * w + line;
                out[index] = 0xFF000000 | ((r / count) << 16) | ((g / count) << 8) | (b / count);
            }
        }
    }
}
