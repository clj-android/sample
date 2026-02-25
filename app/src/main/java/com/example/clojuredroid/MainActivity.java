package com.example.clojuredroid;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Diagnostic activity that validates the Clojure runtime on Android ART
 * and provides interactive nREPL server controls.
 *
 * Tests:
 * 1. Clojure RT initialization
 * 2. Clojure API access
 * 3. Basic Clojure evaluation
 * 4. AOT-compiled namespace loading
 * 5. Clojure function call
 * 6. Classloader inspection
 * 7. Interactive nREPL controls (port, start, stop)
 */
public class MainActivity extends Activity {
    private static final String TAG = "ClojureDroid";
    private static final int DEFAULT_PORT = 7888;

    private LinearLayout logLayout;
    private ScrollView scrollView;
    private TextView nreplStatusView;
    private Button startButton;
    private Button stopButton;
    private EditText portInput;

    // Track whether nREPL namespace has been required
    private volatile boolean nreplNamespaceLoaded = false;
    private volatile boolean isDebugBuild = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        // -- Scrollable log area --
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        logLayout = new LinearLayout(this);
        logLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(logLayout);
        root.addView(scrollView);

        // -- Separator --
        root.addView(makeSeparator());

        // -- nREPL control panel --
        root.addView(makeNreplPanel());

        setContentView(root);

        // Run validation tests
        runTests();
    }

    private void runTests() {
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
            setNreplStatus("Cannot start — Clojure RT not available", 0xFFFF0000);
            return;
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
            setNreplStatus("Cannot start — Clojure API not available", 0xFFFF0000);
            return;
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
                isDebugBuild = true;
                addLog("   AndroidDynamicClassLoader: present (super="
                        + adcl.getSuperclass().getName() + ")", 0xFF00CC00, false);
            } catch (ClassNotFoundException e) {
                addLog("   AndroidDynamicClassLoader: not present (release build)", 0xFFFF8800, false);
            }
        } catch (Throwable t) {
            addLog("6. Classloader check FAILED: " + t, 0xFFFF0000, false);
        }

        // Test 7: Probe nREPL namespace availability
        addLog("", 0, false); // spacer
        addLog("=== nREPL Diagnostics ===", 0xFFFFFFFF, true);

        if (!isDebugBuild) {
            addLog("7. Release build — nREPL infrastructure not available", 0xFFFF8800, false);
            setNreplStatus("Release build — nREPL not available", 0xFFFF8800);
            startButton.setEnabled(false);
            stopButton.setEnabled(false);
            portInput.setEnabled(false);
            return;
        }

        // Check for nREPL classes in the APK
        String[] probeClasses = {
                "nrepl.server",          // would be AOT-compiled class
                "nrepl.core",
                "clj_android.repl.server", // would be AOT-compiled class
        };
        for (String cls : probeClasses) {
            // Check for .clj resource (nREPL ships as source, not AOT)
            String resource = cls.replace('.', '/') + ".clj";
            boolean found = getClass().getClassLoader().getResource(resource) != null;
            addLog("   Resource " + resource + ": " + (found ? "found" : "MISSING"),
                    found ? 0xFF00CC00 : 0xFFFF0000, false);
        }

        // Check if server namespace can be required (do this in background)
        addLog("7. Requiring clj-android.repl.server...", 0xFFCCCC00, false);
        setNreplStatus("Loading nREPL namespace...", 0xFFCCCC00);

        new Thread(
                Thread.currentThread().getThreadGroup(),
                () -> {
                    try {
                        Log.d(TAG, "Requiring clj-android.repl.server namespace");
                        long t0 = System.currentTimeMillis();
                        Object require = clojure.java.api.Clojure.var("clojure.core", "require");
                        ((clojure.lang.IFn) require).invoke(
                                clojure.java.api.Clojure.read("clj-android.repl.server"));
                        long ms = System.currentTimeMillis() - t0;
                        nreplNamespaceLoaded = true;
                        Log.i(TAG, "clj-android.repl.server required in " + ms + "ms");

                        // Check if a server is already running (from ClojureApp auto-start)
                        Object runningFn = clojure.java.api.Clojure.var(
                                "clj-android.repl.server", "running?");
                        boolean alreadyRunning = (boolean) ((clojure.lang.IFn) runningFn).invoke();

                        runOnUiThread(() -> {
                            addLog("   Namespace loaded (" + ms + "ms)", 0xFF00CC00, false);
                            if (alreadyRunning) {
                                addLog("   Server already running (auto-started by ClojureApp)",
                                        0xFF00CC00, false);
                                setNreplStatus("Running on port " + DEFAULT_PORT
                                        + " (auto-started)", 0xFF00CC00);
                                startButton.setEnabled(false);
                                stopButton.setEnabled(true);
                            } else {
                                addLog("   Ready — press Start to launch nREPL", 0xFF00CC00, false);
                                setNreplStatus("Ready — press Start", 0xFF00CC00);
                                startButton.setEnabled(true);
                                stopButton.setEnabled(false);
                            }
                        });
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to require clj-android.repl.server", t);
                        runOnUiThread(() -> {
                            addLog("   FAILED: " + t.getClass().getName()
                                    + ": " + t.getMessage(), 0xFFFF0000, false);
                            // Show cause chain
                            Throwable cause = t.getCause();
                            int depth = 0;
                            while (cause != null && depth < 5) {
                                addLog("     caused by: " + cause.getClass().getName()
                                        + ": " + cause.getMessage(), 0xFFFF0000, false);
                                cause = cause.getCause();
                                depth++;
                            }
                            setNreplStatus("Failed to load nREPL namespace", 0xFFFF0000);
                            startButton.setEnabled(false);
                        });
                    }
                },
                "nREPL-require",
                1048576 // 1MB stack
        ).start();
    }

    // ---- nREPL control panel ----

    private LinearLayout makeNreplPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 16, 0, 0);

        // Status line
        nreplStatusView = new TextView(this);
        nreplStatusView.setText("nREPL: checking...");
        nreplStatusView.setTextSize(16);
        nreplStatusView.setTypeface(Typeface.DEFAULT_BOLD);
        nreplStatusView.setTextColor(0xFFCCCC00);
        nreplStatusView.setPadding(0, 8, 0, 8);
        panel.addView(nreplStatusView);

        // Port row: label + input
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView portLabel = new TextView(this);
        portLabel.setText("Port: ");
        portLabel.setTextSize(16);
        portLabel.setTextColor(0xFFCCCCCC);
        portRow.addView(portLabel);

        portInput = new EditText(this);
        portInput.setText(String.valueOf(DEFAULT_PORT));
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setTextSize(16);
        portInput.setMinimumWidth(200);
        portInput.setTextColor(0xFFFFFFFF);
        portRow.addView(portInput);

        panel.addView(portRow);

        // Button row
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 8, 0, 0);

        startButton = new Button(this);
        startButton.setText("Start nREPL");
        startButton.setEnabled(false); // enabled after namespace loads
        startButton.setOnClickListener(v -> onStartNrepl());
        buttonRow.addView(startButton);

        stopButton = new Button(this);
        stopButton.setText("Stop nREPL");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> onStopNrepl());
        buttonRow.addView(stopButton);

        panel.addView(buttonRow);

        return panel;
    }

    private void onStartNrepl() {
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setNreplStatus("Invalid port number", 0xFFFF0000);
            return;
        }

        startButton.setEnabled(false);
        portInput.setEnabled(false);
        setNreplStatus("Starting nREPL on port " + port + "...", 0xFFCCCC00);
        addLog("", 0, false);
        addLog("Starting nREPL on port " + port + "...", 0xFFCCCC00, false);

        new Thread(
                Thread.currentThread().getThreadGroup(),
                () -> {
                    try {
                        Log.i(TAG, "User requested nREPL start on port " + port);

                        if (!nreplNamespaceLoaded) {
                            logStep("Requiring clj-android.repl.server...");
                            long t0 = System.currentTimeMillis();
                            Object require = clojure.java.api.Clojure.var("clojure.core", "require");
                            ((clojure.lang.IFn) require).invoke(
                                    clojure.java.api.Clojure.read("clj-android.repl.server"));
                            nreplNamespaceLoaded = true;
                            logStep("Namespace loaded (" + (System.currentTimeMillis() - t0) + "ms)");
                        }

                        logStep("Resolving start function...");
                        Object startFn = clojure.java.api.Clojure.var(
                                "clj-android.repl.server", "start");

                        logStep("Calling (start " + port + ")...");
                        long t0 = System.currentTimeMillis();
                        Object server = ((clojure.lang.IFn) startFn).invoke(port);
                        long ms = System.currentTimeMillis() - t0;

                        Log.i(TAG, "nREPL started on port " + port + " in " + ms
                                + "ms, server=" + server);

                        runOnUiThread(() -> {
                            addLog("nREPL started on port " + port + " (" + ms + "ms)",
                                    0xFF00CC00, false);
                            addLog("Connect: adb forward tcp:" + port + " tcp:" + port,
                                    0xFF00CC00, false);
                            setNreplStatus("Running on port " + port, 0xFF00CC00);
                            stopButton.setEnabled(true);
                        });
                    } catch (Throwable t) {
                        Log.e(TAG, "nREPL start failed", t);
                        runOnUiThread(() -> {
                            addLog("nREPL start FAILED: " + t.getClass().getName()
                                    + ": " + t.getMessage(), 0xFFFF0000, false);
                            Throwable cause = t.getCause();
                            int depth = 0;
                            while (cause != null && depth < 5) {
                                addLog("  caused by: " + cause.getClass().getName()
                                        + ": " + cause.getMessage(), 0xFFFF0000, false);
                                cause = cause.getCause();
                                depth++;
                            }
                            setNreplStatus("Start failed — see log above", 0xFFFF0000);
                            startButton.setEnabled(true);
                            portInput.setEnabled(true);
                        });
                    }
                },
                "nREPL-start",
                1048576
        ).start();
    }

    private void onStopNrepl() {
        stopButton.setEnabled(false);
        setNreplStatus("Stopping...", 0xFFCCCC00);
        addLog("Stopping nREPL server...", 0xFFCCCC00, false);

        new Thread(() -> {
            try {
                Object stopFn = clojure.java.api.Clojure.var(
                        "clj-android.repl.server", "stop");
                Object result = ((clojure.lang.IFn) stopFn).invoke();
                Log.i(TAG, "nREPL stopped, result=" + result);

                runOnUiThread(() -> {
                    addLog("nREPL server stopped", 0xFF00CC00, false);
                    setNreplStatus("Stopped — press Start to restart", 0xFFAAAAAA);
                    startButton.setEnabled(true);
                    portInput.setEnabled(true);
                });
            } catch (Throwable t) {
                Log.e(TAG, "nREPL stop failed", t);
                runOnUiThread(() -> {
                    addLog("nREPL stop FAILED: " + t.getMessage(), 0xFFFF0000, false);
                    setNreplStatus("Stop failed", 0xFFFF0000);
                    stopButton.setEnabled(true);
                });
            }
        }, "nREPL-stop").start();
    }

    // ---- UI helpers ----

    private android.view.View makeSeparator() {
        android.view.View sep = new android.view.View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        sep.setBackgroundColor(0xFF444444);
        return sep;
    }

    private void addLog(String text, int color, boolean bold) {
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
    private void logStep(String msg) {
        Log.d(TAG, msg);
        runOnUiThread(() -> addLog("  " + msg, 0xFF888888, false));
    }

    private void setNreplStatus(String text, int color) {
        nreplStatusView.setText("nREPL: " + text);
        nreplStatusView.setTextColor(color);
    }
}
