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
 * Key art that dissolves into whatever is behind it: the image's own alpha fades out toward
 * the left, bottom and top edges, so no colour band marks where the art begins.
 */
public class FadedImageView extends AppCompatImageView {
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient leftFade;
    private LinearGradient bottomFade;
    private LinearGradient topFade;

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
        leftFade = new LinearGradient(0, 0, w * 0.55f, 0,
                new int[] {0x00000000, 0x80000000, 0xFF000000}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        bottomFade = new LinearGradient(0, h, 0, h * 0.6f,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        topFade = new LinearGradient(0, 0, 0, h * 0.22f,
                0x59000000, 0xFF000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() == null || leftFade == null) {
            super.onDraw(canvas);
            return;
        }

        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.onDraw(canvas);
        maskPaint.setShader(leftFade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        maskPaint.setShader(bottomFade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        maskPaint.setShader(topFade);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
        canvas.restoreToCount(save);
    }
}
