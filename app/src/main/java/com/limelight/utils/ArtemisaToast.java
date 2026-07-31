package com.limelight.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;

public final class ArtemisaToast {
    private ArtemisaToast() {
    }

    public static Toast makeText(Context context, int textResId, int duration) {
        return makeText(context, context.getText(textResId), duration);
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        Context appContext = context.getApplicationContext();
        View view = LayoutInflater.from(context).inflate(R.layout.console_toast, null, false);
        TextView textView = view.findViewById(R.id.consoleToastText);
        textView.setText(text);

        Toast toast = new Toast(appContext);
        toast.setDuration(duration);
        int offset = (int) (72 * context.getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offset);
        toast.setView(view);
        return toast;
    }
}
