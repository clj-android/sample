(ns com.example.clojuredroid.main-activity
  "Neko UI DSL demo. Constructs a declarative UI and provides
  functions for REPL-driven hot-reload.

  This namespace is automatically loaded by ClojureActivity when
  com.example.clojuredroid.MainActivity is created.

  To modify the UI live from the REPL:

    (require '[com.example.clojuredroid.main-activity :as ui])
    (reset! ui/*ui-tree
      [:linear-layout {:id-holder true
                       :orientation :vertical
                       :padding [32 32 32 32]}
       [:button {:text \"New button!\"}]])
    (ui/reload-ui!)"
  (:require [neko.ui :as ui]
            [neko.find-view :refer [find-view]]
            [neko.log :as log])
  (:import android.app.Activity
           android.widget.EditText
           android.widget.TextView
           com.goodanser.clj_android.runtime.ClojureActivity))

;; Atom holding the current Activity instance. Set by make-ui
;; so that REPL callers can reload the UI without passing it explicitly.
(defonce *activity (atom nil))

;; Root view returned by make-ui (the id-holder linear-layout).
;; Button click handlers use this with find-view to locate child views.
(defonce *root-view (atom nil))

(def ^:private counter (atom 0))

(defonce ^:private nrepl-ns-loaded? (atom false))

;;  Update this atom from the REPL to change the UI layout.
(defonce *ui-tree
  (atom [:linear-layout {:id-holder true
                    :orientation :vertical
                    :padding [32 32 32 32]}
    [:text-view {:text "Clojure on Android"
                 :text-size [24 :sp]}]]))

(defn make-ui
  "Builds the sample UI tree using neko's declarative DSL.
  Called by ClojureActivity.reloadUi() and by on-create.
  Reads the UI tree from *ui-tree if set, otherwise uses the default."
  [^Activity activity]
  (reset! *activity activity)
  (let [root (ui/make-ui activity @*ui-tree)]
    (reset! *root-view root)
    root))

(defn on-create
  "Called automatically by ClojureActivity when the activity is created.
  Sets up the initial UI."
  [^Activity activity saved-instance-state]
  (let [view (make-ui activity)]
    (.setFitsSystemWindows view true)
    (.setContentView activity view)))

(defn reload-ui!
  "Hot-reload the UI from the REPL. Uses ClojureActivity's
  built-in reloadUi mechanism."
  []
  (when-let [activity (ClojureActivity/getInstance
                        "com.example.clojuredroid.main-activity")]
    (.reloadUi ^ClojureActivity activity)))

(add-watch *ui-tree :ui-reload-watch
           (fn [_key _ref _old _new]
             (reload-ui!)))

;; --- nREPL controls ---

(defn- run-on-ui! [f]
  (when-let [^Activity activity @*activity]
    (.runOnUiThread activity f)))

(defn- nrepl-set-status! [text color]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @*root-view ::nrepl-status)]
        (.setText v (str text))
        (.setTextColor v (unchecked-int color))))))

(defn- nrepl-set-error! [text]
  (run-on-ui!
    (fn []
      (when-let [^TextView v (find-view @*root-view ::nrepl-error)]
        (.setText v (str text))))))

(defn- nrepl-set-buttons! [start-enabled? stop-enabled?]
  (run-on-ui!
    (fn []
      (some-> (find-view @*root-view ::nrepl-start-btn)
              (.setEnabled (boolean start-enabled?)))
      (some-> (find-view @*root-view ::nrepl-stop-btn)
              (.setEnabled (boolean stop-enabled?))))))

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

(defn- on-start-nrepl [_view]
  (when-let [root @*root-view]
    (let [port (some-> (find-view root ::nrepl-port-input)
                       (parse-port))]
      (if-not port
        (nrepl-set-error! "Invalid port (1\u201365535)")
        (do
          (nrepl-set-error! "")
          (nrepl-set-status! "Starting..." 0xFFCCCC00)
          (nrepl-set-buttons! false false)
          (.start
            (Thread.
              (.getThreadGroup (Thread/currentThread))
              (fn []
                (try
                  (when-not @nrepl-ns-loaded?
                    (nrepl-set-status! "Loading nREPL..." 0xFFCCCC00)
                    (require 'clj-android.repl.server)
                    (reset! nrepl-ns-loaded? true))
                  ((resolve 'clj-android.repl.server/start) port)
                  (nrepl-set-status! (str "Running on port " port) 0xFF00CC00)
                  (nrepl-set-buttons! false true)
                  (catch Throwable t
                    (nrepl-set-status! "Error" 0xFFFF0000)
                    (nrepl-set-error! (.getMessage t))
                    (nrepl-set-buttons! true false))))
              "nrepl-start"
              1048576)))))))

(defn- on-stop-nrepl [_view]
  (nrepl-set-status! "Stopping..." 0xFFCCCC00)
  (nrepl-set-buttons! false false)
  (.start
    (Thread.
      (fn []
        (try
          (when @nrepl-ns-loaded?
            ((resolve 'clj-android.repl.server/stop)))
          (nrepl-set-status! "Stopped" 0xFFAAAAAA)
          (nrepl-set-error! "")
          (nrepl-set-buttons! true false)
          (catch Throwable t
            (nrepl-set-status! "Error" 0xFFFF0000)
            (nrepl-set-error! (.getMessage t))
            (nrepl-set-buttons! false true))))
      "nrepl-stop")))

(reset! *ui-tree
        [:linear-layout {:id-holder true
                         :orientation :vertical
                         :padding [32 32 32 32]}
         [:text-view {:text "Clojure on Android"
                      :text-size [24 :sp]}]
         [:text-view {:text "Built with neko UI DSL"
                      :text-size [16 :sp]}]
         [:text-view {:text (str "Counter: " @counter)
                      :text-size [20 :sp]
                      :padding [0 8 0 8]
                      :id ::counter-display}]
         [:button {:text "Increment"
                   :on-click (fn [_]
                               (let [v (swap! counter inc)]
                                 (.setText ^TextView (find-view @*root-view ::counter-display)
                                           (str "Counter: " v))))}]
         [:button {:text "Reset"
                   :on-click (fn [_]
                               (reset! counter 0)
                               (.setText ^TextView (find-view @*root-view ::counter-display)
                                         "Counter: 0"))}]
         ;; --- nREPL section ---
         [:text-view {:text "nREPL Server"
                      :text-size [20 :sp]
                      :padding [0 24 0 8]}]
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
                      :text-color (unchecked-int 0xFFAAAAAA)
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
                      :text-color (unchecked-int 0xFFFF0000)
                      :padding [0 4 0 0]}]])
