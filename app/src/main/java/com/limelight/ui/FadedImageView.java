package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * The Showcase cover: the image's own alpha fades out toward the right and bottom edges, so it
 * dissolves into the backdrop with no colour band marking where it ends.
 */
public class FadedImageView extends AppCompatImageView {
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient rightFade;
    private LinearGradient bottomFade;

    public FadedImageView(Context context) {
        this(context, null);
    }

    public FadedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FadedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rightFade = new LinearGradient(w, 0, w * 0.5f, 0,
                new int[] {0x00000000, 0x80000000, 0xFF000000}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        bottomFade = new LinearGradient(0, h, 0, h * 0.65f,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() == null || rightFade == null) {
            super.onDraw(canvas);
            return;
        }

        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.onDraw(canvas);
        maskPaint.setShader(rightFade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        maskPaint.setShader(bottomFade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        canvas.restoreToCount(save);
    }
}
