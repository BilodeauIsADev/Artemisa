package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;
    private String pin;
    private String help;

    private AlertDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert.isShowing()) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    /** Pairing dialog: the PIN as large digits (or none for OTP pairing), plus a help line. */
    public static void displayPinDialog(Activity activity, String title, String message, String pin, String help)
    {
        Dialog dialog = new Dialog(activity, title, message, () -> {});
        dialog.pin = pin;
        dialog.help = help;
        activity.runOnUiThread(dialog);
    }

    private View createPinView() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_pair_pin, null, false);
        ((TextView) view.findViewById(R.id.pairTitle)).setText(title);
        ((TextView) view.findViewById(R.id.pairMessage)).setText(message);
        ((TextView) view.findViewById(R.id.pairHelp)).setText(help);

        LinearLayout digits = view.findViewById(R.id.pairPinDigits);
        if (pin == null) {
            digits.setVisibility(View.GONE);
        }
        else {
            for (int i = 0; i < pin.length(); i++) {
                TextView digit = (TextView) LayoutInflater.from(activity)
                        .inflate(R.layout.dialog_pair_pin_digit, digits, false);
                digit.setText(String.valueOf(pin.charAt(i)));
                digits.addView(digit);
            }
            digits.setContentDescription(pin);
        }
        return view;
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = new AlertDialog.Builder(activity).create();

        if (pin != null || help != null) {
            alert.setView(createPinView());
        }
        else {
            alert.setTitle(title);
            alert.setMessage(message);
        }
        alert.setCancelable(false);
        alert.setCanceledOnTouchOutside(false);
 
        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getText(android.R.string.ok), new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                  synchronized (rundownDialogs) {
                      rundownDialogs.remove(Dialog.this);
                      alert.dismiss();
                  }

                  runOnDismiss.run();
              }
        });
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getResources().getText(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                synchronized (rundownDialogs) {
                    rundownDialogs.remove(Dialog.this);
                    alert.dismiss();
                }

                runOnDismiss.run();

                HelpLauncher.launchTroubleshooting(activity);
            }
        });
        alert.setOnShowListener(new DialogInterface.OnShowListener(){

            @Override
            public void onShow(DialogInterface dialog) {
                // Set focus to the OK button by default
                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setFocusable(true);
                button.setFocusableInTouchMode(true);
                button.requestFocus();
            }
        });

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }

}
