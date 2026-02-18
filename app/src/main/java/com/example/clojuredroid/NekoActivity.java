package com.example.clojuredroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * Activity that demonstrates neko UI DSL for declarative Android UI.
 * The UI is defined entirely in Clojure (com.example.clojuredroid.ui)
 * and can be hot-reloaded via nREPL.
 */
public class NekoActivity extends Activity {
    private static final String TAG = "NekoActivity";
    private static NekoActivity currentInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentInstance = this;

        try {
            // Require the Clojure namespace that defines our UI
            Object require = clojure.java.api.Clojure.var("clojure.core", "require");
            ((clojure.lang.IFn) require).invoke(
                clojure.java.api.Clojure.read("com.example.clojuredroid.ui"));

            // Call make-ui to construct the view hierarchy
            Object makeUi = clojure.java.api.Clojure.var(
                "com.example.clojuredroid.ui", "make-ui");
            View view = (View) ((clojure.lang.IFn) makeUi).invoke(this);
            view.setFitsSystemWindows(true);
            setContentView(view);

            Log.i(TAG, "Neko UI loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load neko UI", e);
            // Fall back to showing error
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("Error loading neko UI: " + e.getMessage());
            tv.setPadding(32, 32, 32, 32);
            setContentView(tv);
        }
    }

    /**
     * Reload the UI on the main thread. Called from Clojure REPL via
     * reflection. Uses a Java Runnable (not Clojure reify) for reliable
     * dispatch through Android's message loop.
     */
    public static void reloadUi() {
        final NekoActivity activity = currentInstance;
        if (activity == null) {
            Log.e(TAG, "No activity instance available for reload");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    clojure.lang.IFn makeUi = (clojure.lang.IFn)
                        clojure.java.api.Clojure.var(
                            "com.example.clojuredroid.ui", "make-ui");
                    View view = (View) makeUi.invoke(activity);
                    view.setFitsSystemWindows(true);
                    activity.setContentView(view);
                    Log.i(TAG, "UI reloaded via REPL");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to reload UI", e);
                }
            }
        });
    }
}
