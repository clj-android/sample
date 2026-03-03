(ns com.example.clojuredroid.test-activity
  "Clojure companion for TestActivity. Handles nREPL diagnostics (test 7)
  and interactive nREPL controls, while Java tests 1-6 remain in
  TestActivity.java for intentional Java↔Clojure interop validation."
  (:require [clj-android.repl.server :as repl-server])
  (:import android.graphics.Typeface
           android.text.InputType
           android.view.Gravity
           android.widget.Button
           android.widget.EditText
           android.widget.LinearLayout
           android.widget.LinearLayout$LayoutParams
           android.widget.ScrollView
           android.widget.TextView
           com.example.clojuredroid.TestActivity))

(declare on-start-nrepl on-stop-nrepl)

(def ^:private default-port 7888)

(defn- make-nrepl-panel
  "Builds the nREPL control panel and sets the activity's public fields."
  [^TestActivity activity]
  (let [panel (LinearLayout. activity)]
    (.setOrientation panel LinearLayout/VERTICAL)
    (.setPadding panel 0 16 0 0)

    ;; Status line
    (let [status (TextView. activity)]
      (.setText status "nREPL: checking...")
      (.setTextSize status 16)
      (.setTypeface status Typeface/DEFAULT_BOLD)
      (.setTextColor status (unchecked-int 0xFFCCCC00))
      (.setPadding status 0 8 0 8)
      (set! (.nreplStatusView activity) status)
      (.addView panel status))

    ;; Port row: label + input
    (let [port-row (LinearLayout. activity)]
      (.setOrientation port-row LinearLayout/HORIZONTAL)
      (.setGravity port-row Gravity/CENTER_VERTICAL)

      (let [label (TextView. activity)]
        (.setText label "Port: ")
        (.setTextSize label 16)
        (.setTextColor label (unchecked-int 0xFFCCCCCC))
        (.addView port-row label))

      (let [input (EditText. activity)]
        (.setText input (str default-port))
        (.setInputType input InputType/TYPE_CLASS_NUMBER)
        (.setTextSize input 16)
        (.setMinimumWidth input 200)
        (.setTextColor input (unchecked-int 0xFFFFFFFF))
        (set! (.portInput activity) input)
        (.addView port-row input))

      (.addView panel port-row))

    ;; Button row
    (let [button-row (LinearLayout. activity)]
      (.setOrientation button-row LinearLayout/HORIZONTAL)
      (.setPadding button-row 0 8 0 0)

      (let [start-btn (Button. activity)]
        (.setText start-btn "Start nREPL")
        (.setEnabled start-btn false)
        (.setOnClickListener start-btn
          (reify android.view.View$OnClickListener
            (onClick [_ _] (on-start-nrepl activity))))
        (set! (.startButton activity) start-btn)
        (.addView button-row start-btn))

      (let [stop-btn (Button. activity)]
        (.setText stop-btn "Stop nREPL")
        (.setEnabled stop-btn false)
        (.setOnClickListener stop-btn
          (reify android.view.View$OnClickListener
            (onClick [_ _] (on-stop-nrepl activity))))
        (set! (.stopButton activity) stop-btn)
        (.addView button-row stop-btn))

      (.addView panel button-row))

    panel))

(defn- run-nrepl-diagnostics
  "Runs nREPL diagnostic test 7: checks availability and server status."
  [^TestActivity activity]
  (.addLog activity "" 0 false) ; spacer
  (.addLog activity "=== nREPL Diagnostics ===" (unchecked-int 0xFFFFFFFF) true)

  (if-not (repl-server/repl-available?)
    (do
      (.addLog activity "7. Release build — nREPL infrastructure not available"
               (unchecked-int 0xFFFF8800) false)
      (.setNreplStatus activity "Release build — nREPL not available"
                        (unchecked-int 0xFFFF8800))
      (.setEnabled (.startButton activity) false)
      (.setEnabled (.stopButton activity) false)
      (.setEnabled (.portInput activity) false))
    ;; Debug build — check nREPL resource availability
    (do
      (doseq [cls ["nrepl/server.clj" "nrepl/core.clj" "clj_android/repl/server.clj"]]
        (let [found (some? (.. (class activity) getClassLoader (getResource cls)))]
          (.addLog activity (str "   Resource " cls ": " (if found "found" "MISSING"))
                   (unchecked-int (if found 0xFF00CC00 0xFFFF0000)) false)))

      (.addLog activity "7. clj-android.repl.server loaded" (unchecked-int 0xFF00CC00) false)

      (if (repl-server/running?)
        (let [p (or (repl-server/port) default-port)]
          (.addLog activity (str "   Server already running on port " p " (auto-started by ClojureApp)")
                   (unchecked-int 0xFF00CC00) false)
          (.setNreplStatus activity (str "Running on port " p " (auto-started)")
                           (unchecked-int 0xFF00CC00))
          (.setEnabled (.startButton activity) false)
          (.setEnabled (.stopButton activity) true))
        (do
          (.addLog activity "   Ready — press Start to launch nREPL"
                   (unchecked-int 0xFF00CC00) false)
          (.setNreplStatus activity "Ready — press Start"
                           (unchecked-int 0xFF00CC00))
          (.setEnabled (.startButton activity) true)
          (.setEnabled (.stopButton activity) false))))))

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

(defn- on-start-nrepl [^TestActivity activity]
  (let [port (parse-port (.portInput activity))]
    (if-not port
      (.setNreplStatus activity "Invalid port number" (unchecked-int 0xFFFF0000))
      (do
        (.setEnabled (.startButton activity) false)
        (.setEnabled (.portInput activity) false)
        (.setNreplStatus activity (str "Starting nREPL on port " port "...")
                         (unchecked-int 0xFFCCCC00))
        (.addLog activity "" 0 false)
        (.addLog activity (str "Starting nREPL on port " port "...")
                 (unchecked-int 0xFFCCCC00) false)
        (.start
          (Thread.
            (.getThreadGroup (Thread/currentThread))
            (fn []
              (try
                (.logStep activity "Calling (start)...")
                (let [t0 (System/currentTimeMillis)]
                  (repl-server/start port)
                  (let [ms (- (System/currentTimeMillis) t0)
                        actual-port (or (repl-server/port) port)]
                    (.runOnUiThread activity
                      (fn []
                        (.addLog activity
                                 (str "nREPL started on port " actual-port " (" ms "ms)")
                                 (unchecked-int 0xFF00CC00) false)
                        (.addLog activity
                                 (str "Connect: adb forward tcp:" actual-port " tcp:" actual-port)
                                 (unchecked-int 0xFF00CC00) false)
                        (.setNreplStatus activity (str "Running on port " actual-port)
                                         (unchecked-int 0xFF00CC00))
                        (.setEnabled (.stopButton activity) true)))))
                (catch Throwable t
                  (.runOnUiThread activity
                    (fn []
                      (.addLog activity
                               (str "nREPL start FAILED: " (.getName (class t))
                                    ": " (.getMessage t))
                               (unchecked-int 0xFFFF0000) false)
                      (.setNreplStatus activity "Start failed — see log above"
                                       (unchecked-int 0xFFFF0000))
                      (.setEnabled (.startButton activity) true)
                      (.setEnabled (.portInput activity) true))))))
            "nREPL-start"
            1048576))))))

(defn- on-stop-nrepl [^TestActivity activity]
  (.setEnabled (.stopButton activity) false)
  (.setNreplStatus activity "Stopping..." (unchecked-int 0xFFCCCC00))
  (.addLog activity "Stopping nREPL server..." (unchecked-int 0xFFCCCC00) false)
  (.start
    (Thread.
      (fn []
        (try
          (repl-server/stop)
          (.runOnUiThread activity
            (fn []
              (.addLog activity "nREPL server stopped" (unchecked-int 0xFF00CC00) false)
              (.setNreplStatus activity "Stopped — press Start to restart"
                               (unchecked-int 0xFFAAAAAA))
              (.setEnabled (.startButton activity) true)
              (.setEnabled (.portInput activity) true)))
          (catch Throwable t
            (.runOnUiThread activity
              (fn []
                (.addLog activity (str "nREPL stop FAILED: " (.getMessage t))
                         (unchecked-int 0xFFFF0000) false)
                (.setNreplStatus activity "Stop failed" (unchecked-int 0xFFFF0000))
                (.setEnabled (.stopButton activity) true))))))
      "nREPL-stop")))

(defn on-create
  "Called by ClojureActivity when TestActivity is created.
  Builds the layout, runs Java diagnostics 1-6, then handles
  nREPL diagnostics and controls in Clojure."
  [^TestActivity activity _saved-instance-state]
  (let [root (LinearLayout. activity)]
    (.setOrientation root LinearLayout/VERTICAL)
    (.setPadding root 32 32 32 32)

    ;; Scrollable log area
    (let [sv (ScrollView. activity)]
      (.setLayoutParams sv (LinearLayout$LayoutParams.
                             LinearLayout$LayoutParams/MATCH_PARENT
                             0 1.0))
      (let [ll (LinearLayout. activity)]
        (.setOrientation ll LinearLayout/VERTICAL)
        (set! (.logLayout activity) ll)
        (.addView sv ll))
      (set! (.scrollView activity) sv)
      (.addView root sv))

    ;; Separator
    (.addView root (.makeSeparator activity))

    ;; nREPL control panel
    (.addView root (make-nrepl-panel activity))

    (.setContentView activity root)

    ;; Run Java diagnostic tests 1-6
    (let [basic-ok (.runTests activity)]
      (when basic-ok
        ;; Run nREPL diagnostics (test 7) in Clojure
        (run-nrepl-diagnostics activity)))))
