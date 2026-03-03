package com.example.clojuredroid;

import android.graphics.Typeface;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.goodanser.clj_android.runtime.ClojureActivity;

/**
 * Diagnostic activity that validates the Clojure runtime on Android ART
 * and provides interactive nREPL server controls.
 *
 * Extends ClojureActivity, which auto-requires the Clojure namespace
 * com.example.clojuredroid.test-activity and delegates lifecycle calls.
 * Java tests 1-6 validate Java↔Clojure interop; nREPL diagnostics
 * and controls are handled by the Clojure namespace.
 */
public class TestActivity extends ClojureActivity {
    private static final String TAG = "ClojureDroid";

    // Public fields set by test_activity.clj during on-create
    public LinearLayout logLayout;
    public ScrollView scrollView;
    public TextView nreplStatusView;
    public Button startButton;
    public Button stopButton;
    public EditText portInput;

    /**
     * Runs Java↔Clojure interop diagnostic tests 1-6.
     * Returns true if basic runtime tests passed (RT + API accessible),
     * false if a critical failure prevents nREPL from working.
     */
    public boolean runTests() {
        addLog("=== Clojure-Android Diagnostics ===", 0xFFFFFFFF, true);
        addLog("VM: " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version"), 0xFFAAAAAA, false);

        // Test 1: Clojure RT initialization
        try {
            long start = System.currentTimeMillis();
            Class<?> rt = Class.forName("clojure.lang.RT");
            long ms = System.currentTimeMillis() - start;
            addLog("1. RT class loaded (" + ms + "ms)", 0xFF00CC00, false);
            addLog("   loader=" + rt.getClassLoader(), 0xFF888888, false);
        } catch (Throwable t) {
            addLog("1. RT class load FAILED: " + t, 0xFFFF0000, false);
            Log.e(TAG, "RT load failed", t);
            return false;
        }

        // Test 2: Clojure API accessible
        try {
            long start = System.currentTimeMillis();
            Object var = clojure.java.api.Clojure.var("clojure.core", "+");
            long ms = System.currentTimeMillis() - start;
            addLog("2. Clojure API accessible (" + ms + "ms)", 0xFF00CC00, false);
        } catch (Throwable t) {
            addLog("2. Clojure API FAILED: " + t, 0xFFFF0000, false);
            Log.e(TAG, "Clojure API failed", t);
            return false;
        }

        // Test 3: Basic Clojure evaluation
        try {
            long start = System.currentTimeMillis();
            Object plus = clojure.java.api.Clojure.var("clojure.core", "+");
            Object result = ((clojure.lang.IFn) plus).invoke(1L, 2L);
            long ms = System.currentTimeMillis() - start;
            boolean ok = "3".equals(result.toString());
            addLog("3. (+ 1 2) = " + result + " (" + ms + "ms)",
                    ok ? 0xFF00CC00 : 0xFFFF0000, false);
        } catch (Throwable t) {
            addLog("3. Basic eval FAILED: " + t, 0xFFFF0000, false);
            Log.e(TAG, "Basic eval failed", t);
        }

        // Test 4: AOT-compiled namespace loading
        try {
            long start = System.currentTimeMillis();
            Object require = clojure.java.api.Clojure.var("clojure.core", "require");
            ((clojure.lang.IFn) require).invoke(
                    clojure.java.api.Clojure.read("com.example.clojuredroid.hello"));
            long ms = System.currentTimeMillis() - start;
            addLog("4. Namespace loaded (" + ms + "ms)", 0xFF00CC00, false);
        } catch (Throwable t) {
            addLog("4. Namespace load FAILED: " + t, 0xFFFF0000, false);
            Log.e(TAG, "Namespace load failed", t);
        }

        // Test 5: Call function from AOT-compiled namespace
        try {
            Object greeting = clojure.java.api.Clojure.var(
                    "com.example.clojuredroid.hello", "greeting");
            Object result = ((clojure.lang.IFn) greeting).invoke();
            addLog("5. greeting = \"" + result + "\"", 0xFF00CC00, false);
        } catch (Throwable t) {
            addLog("5. Function call FAILED: " + t, 0xFFFF0000, false);
            Log.e(TAG, "Function call failed", t);
        }

        // Test 6: Classloader inspection
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            String clType = cl != null ? cl.getClass().getName() : "null";
            addLog("6. Context classloader: " + clType, 0xFF00CC00, false);

            // Probe for DynamicClassLoader
            try {
                Class<?> dcl = Class.forName("clojure.lang.DynamicClassLoader");
                addLog("   DynamicClassLoader: present (super="
                        + dcl.getSuperclass().getName() + ")", 0xFF00CC00, false);
            } catch (ClassNotFoundException e) {
                addLog("   DynamicClassLoader: NOT FOUND", 0xFFFF8800, false);
            }

            // Probe for AndroidDynamicClassLoader
            try {
                Class<?> adcl = Class.forName("clojure.lang.AndroidDynamicClassLoader");
                addLog("   AndroidDynamicClassLoader: present (super="
                        + adcl.getSuperclass().getName() + ")", 0xFF00CC00, false);
            } catch (ClassNotFoundException e) {
                addLog("   AndroidDynamicClassLoader: not present (release build)", 0xFFFF8800, false);
            }
        } catch (Throwable t) {
            addLog("6. Classloader check FAILED: " + t, 0xFFFF0000, false);
        }

        return true;
    }

    // ---- UI helpers (called from test_activity.clj) ----

    public android.view.View makeSeparator() {
        android.view.View sep = new android.view.View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        sep.setBackgroundColor(0xFF444444);
        return sep;
    }

    public void addLog(String text, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 2, 0, 2);
        logLayout.addView(tv);
        // Auto-scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        if (color == 0xFFFF0000) {
            Log.e(TAG, text);
        } else {
            Log.i(TAG, text);
        }
    }

    /** Log a step from a background thread to both UI and logcat. */
    public void logStep(String msg) {
        Log.d(TAG, msg);
        runOnUiThread(() -> addLog("  " + msg, 0xFF888888, false));
    }

    public void setNreplStatus(String text, int color) {
        if (nreplStatusView != null) {
            nreplStatusView.setText("nREPL: " + text);
            nreplStatusView.setTextColor(color);
        }
    }
}
