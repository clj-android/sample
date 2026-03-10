(ns com.example.clojuredroid.repl
  "nREPL server controls for the sample app.

  Call (init! activity root-view) from the main activity's on-create
  to set references for UI updates."
  (:require [neko.find-view :refer [find-view]]
            [clj-android.repl.server :as repl-server])
  (:import android.app.Activity
           android.widget.EditText
           android.widget.TextView))

;; Set by main-activity via init!
(defonce *activity (atom nil))
(defonce *root-view (atom nil))

(defn init!
  "Provides activity and root-view references for UI updates."
  [activity root-view]
  (reset! *activity activity)
  (reset! *root-view root-view))

;; --- UI helpers ---

(defn- run-on-ui! [f]
  (when-let [^Activity activity @*activity]
    (.runOnUiThread activity f)))

(defn- set-status! [text color]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @*root-view ::nrepl-status)]
        (.setText v (str text))
        (.setTextColor v (unchecked-int color))))))

(defn- set-error! [text]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @*root-view ::nrepl-error)]
        (.setText v (str text))))))

(defn- set-buttons! [start-enabled? stop-enabled?]
  (run-on-ui!
    (fn []
      (when-let [^android.view.View v (find-view @*root-view ::nrepl-start-btn)]
        (.setEnabled v (boolean start-enabled?)))
      (when-let [^android.view.View v (find-view @*root-view ::nrepl-stop-btn)]
        (.setEnabled v (boolean stop-enabled?))))))

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

;; --- nREPL actions ---

(defn- on-start-nrepl [_view]
  (when-let [root @*root-view]
    (let [port (some-> (find-view root ::nrepl-port-input) (parse-port))]
      (if-not port
        (set-error! "Invalid port (1\u201365535)")
        (do
          (set-error! "")
          (set-status! "Starting..." 0xFFCCCC00)
          (set-buttons! false false)
          (.start
            (Thread.
              (.getThreadGroup (Thread/currentThread))
              (fn []
                (try
                  (if (repl-server/running?)
                    (set-status! (str "Running on port "
                                      (or (repl-server/port) port))
                                 0xFF00CC00)
                    (repl-server/start port))
                  (set-status! (str "Running on port "
                                    (or (repl-server/port) port))
                               0xFF00CC00)
                  (set-buttons! false true)
                  (catch Throwable t
                    (set-status! "Error" 0xFFFF0000)
                    (set-error! (.getMessage t))
                    (set-buttons! true false))))
              "nrepl-start"
              1048576)))))))

(defn- on-stop-nrepl [_view]
  (set-status! "Stopping..." 0xFFCCCC00)
  (set-buttons! false false)
  (.start
    (Thread.
      (fn []
        (try
          (repl-server/stop)
          (set-status! "Stopped" 0xFFAAAAAA)
          (set-error! "")
          (set-buttons! true false)
          (catch Throwable t
            (set-status! "Error" 0xFFFF0000)
            (set-error! (.getMessage t))
            (set-buttons! false true))))
      "nrepl-stop")))

(defn sync-status!
  "Waits for nREPL auto-start completion and updates the UI."
  []
  (.start
    (Thread.
      (fn []
        (loop [waited 0]
          (when (and (nil? @*root-view) (< waited 10000))
            (Thread/sleep 500)
            (recur (+ waited 500))))
        (when @*root-view
          (set-status! "Starting..." 0xFFCCCC00)
          (when-not (repl-server/repl-available?)
            (set-status! "Unavailable" 0xFFFF0000))
          (set-buttons! false false)
          (if (repl-server/wait-for-ready :timeout-ms 60000)
            (let [p (or (repl-server/port) 7888)]
              (set-status! (str "Running on port " p) 0xFF00CC00)
              (set-buttons! false true))
            (when-not (repl-server/running?)
              (set-status! "Stopped" 0xFFAAAAAA)
              (set-buttons! true false)))))
      "nrepl-status-sync")))

;; --- Section UI ---

(defn section-ui
  "Returns the nREPL controls section UI tree."
  [section-id]
  [:scroll-view {:id section-id
                 :layout-width :fill
                 :layout-height :fill
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 16]
                    :layout-width :match-parent}
    [:text-view {:text "nREPL Server"
                 :text-size [22 :sp]
                 :padding [0 0 0 8]}]
    [:text-view {:text "Connect from your editor to live-reload code."
                 :text-size [14 :sp]
                 :text-color 0xFF888888
                 :padding [0 0 0 16]}]
    [:linear-layout {:orientation :horizontal}
     [:text-view {:text "Port: "
                  :text-size [16 :sp]
                  :padding [0 8 8 0]}]
     [:edit-text {:id ::nrepl-port-input
                  :text "7888"
                  :input-type :number}]]
    [:text-view {:id ::nrepl-status
                 :text "Stopped"
                 :text-size [16 :sp]
                 :text-color 0xFFAAAAAA
                 :padding [0 4 0 4]}]
    [:linear-layout {:orientation :horizontal
                     :padding [0 4 0 4]}
     [:button {:id ::nrepl-start-btn
               :text "Start"
               :on-click on-start-nrepl}]
     [:button {:id ::nrepl-stop-btn
               :text "Stop"
               :enabled false
               :on-click on-stop-nrepl}]]
    [:text-view {:id ::nrepl-error
                 :text ""
                 :text-size [14 :sp]
                 :text-color 0xFFFF0000
                 :padding [0 4 0 0]}]]])
