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
            [neko.log :as log]
            [neko.reactive :refer [cell cell=]]
            [neko.ui.support.material]
            [clj-android.repl.server :as repl-server]
            [neko.doc :refer [describe]])
  (:import android.app.Activity
           android.view.View
           android.widget.EditText
           android.widget.TextView
           [com.google.android.material.tabs TabLayout TabLayout$Tab]
           com.goodanser.clj_android.runtime.ClojureActivity))

;; Atom holding the current Activity instance. Set by make-ui
;; so that REPL callers can reload the UI without passing it explicitly.
(defonce *activity (atom nil))

;; Root view returned by make-ui (the id-holder linear-layout).
;; Button click handlers use this with find-view to locate child views.
(defonce *root-view (atom nil))

;;  Update this atom from the REPL to change the UI layout.
(defonce *ui-tree
  (atom [:linear-layout {:id-holder true
                    :orientation :vertical
                    :padding [32 32 32 32]}
    [:text-view {:text "Clojure on Android"
                 :text-size [24 :sp]}]]))

(defn- setup-tabs!
  "Adds tab items to the TabLayout after the UI tree is built."
  [root]
  (when-let [^TabLayout tabs (find-view root ::tabs)]
    (let [t1 (doto (.newTab tabs) (.setText "Widgets"))
          t2 (doto (.newTab tabs) (.setText "REPL"))]
      (.addTab tabs t1)
      (.addTab tabs t2))))

(defn make-ui
  "Builds the sample UI tree using neko's declarative DSL.
  Called by ClojureActivity.reloadUi() and by on-create.
  Reads the UI tree from *ui-tree if set, otherwise uses the default."
  [^Activity activity]
  (reset! *activity activity)
  (let [root (ui/make-ui activity @*ui-tree)]
    (reset! *root-view root)
    (setup-tabs! root)
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

;; --- Tab switching ---

(defn- on-tab-selected [tab]
  (when-let [root @*root-view]
    (let [^View widgets (find-view root ::widgets-tab)
          ^View repl-tab (find-view root ::repl-tab)]
      (when (and widgets repl-tab)
        (case (.getPosition ^TabLayout$Tab tab)
          0 (do (.setVisibility widgets View/VISIBLE)
                (.setVisibility repl-tab View/GONE))
          1 (do (.setVisibility widgets View/GONE)
                (.setVisibility repl-tab View/VISIBLE))
          nil)))))

;; --- nREPL controls ---
;; These update values in the UI the old fashioned way

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
                  (if (repl-server/running?)
                    (nrepl-set-status! (str "Running on port "
                                            (or (repl-server/port) port))
                                       0xFF00CC00)
                    (repl-server/start port))
                  (nrepl-set-status! (str "Running on port "
                                          (or (repl-server/port) port))
                                     0xFF00CC00)
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
          (repl-server/stop)
          (nrepl-set-status! "Stopped" 0xFFAAAAAA)
          (nrepl-set-error! "")
          (nrepl-set-buttons! true false)
          (catch Throwable t
            (nrepl-set-status! "Error" 0xFFFF0000)
            (nrepl-set-error! (.getMessage t))
            (nrepl-set-buttons! false true))))
      "nrepl-stop")))

(defn- sync-nrepl-status!
  "Waits for nREPL auto-start completion and updates the UI.
  Shows 'Starting...' while loading, then 'Running' once ready."
  []
  (.start
    (Thread.
      (fn []
        ;; Wait for UI to be ready before showing status
        (loop [waited 0]
          (when (and (nil? @*root-view) (< waited 10000))
            (Thread/sleep 500)
            (recur (+ waited 500))))
        (when @*root-view
          (nrepl-set-status! "Starting..." 0xFFCCCC00)
          (when-not (repl-server/repl-available?)
            (nrepl-set-status! "Unavailable" 0xFFFF0000))
          (nrepl-set-buttons! false false)
          (if (repl-server/wait-for-ready :timeout-ms 60000)
            (let [p (or (repl-server/port) 7888)]
              (nrepl-set-status! (str "Running on port " p) 0xFF00CC00)
              (nrepl-set-buttons! false true))
            ;; Timeout — server didn't start
            (when-not (repl-server/running?)
              (nrepl-set-status! "Stopped" 0xFFAAAAAA)
              (nrepl-set-buttons! true false)))))
      "nrepl-status-sync")))

;; Detect nREPL auto-started by ClojureApp and sync the UI status.

(add-watch *ui-tree :nrepl-status-watch
           (fn [_key _ref _old _new]
             (sync-nrepl-status!)))

(def default-padding [12 12 12 12])


;; Here, we use neko.reactive cells. Changing the value of a cell updates the UI.
;; This is experimental, but it should make UI programming much nicer.
;; cell is like an atom, cell= takes a function of zero arguments that references cells.
;; Use the cell= as the value of a trait or attribute and the UI will track its value.

(def counter (cell 0))
(def counter-text= (cell= #(str "Counter: " @counter)))

(def seek-progress (cell 50))
(def seek-text= (cell= #(str "Progress: " @seek-progress "%")))
(def seek= (cell= #(deref seek-progress)))

(def show-message (cell false))
(def show-message= (cell= #(if @show-message :visible :gone)))

(reset! *ui-tree
        [:linear-layout {:id-holder true
                         :orientation :vertical}
         ;; Tab bar
         [:tab-layout {:id ::tabs
                       :tab-mode :fixed
                       :tab-gravity :fill
                       :layout-width :fill
                       :on-tab-selected on-tab-selected}]
         ;; --- Tab 1: Widget demos ---
         [:scroll-view {:id ::widgets-tab
                        :layout-width :fill
                        :layout-height 0
                        :layout-weight 1}
          [:linear-layout {:orientation :vertical
                           :padding [32 32 32 32]
                           :layout-width :match-parent}
           [:text-view {:text "Clojure on Android"
                        :text-size [24 :sp]
                        :gravity :center
                        :background-color (unchecked-int 0xFF334455)
                        :text-color (unchecked-int 0xFFFFFFFF)
                        :padding [24 12 12 12]
                        :layout-width :fill
                        :content-description "Demo text with background color"}]
           [:text-view {:text "Built with neko UI DSL"
                        :text-size [16 :sp]}]
           [:text-view {:text counter-text
                        :text-size [20 :sp]
                        :padding default-padding}]
           [:linear-layout {:orientation :horizontal}
            [:button {:text "Increment"
                      :on-click (fn [_] (swap! counter inc))}]
            [:button {:text "Reset"
                      :on-click (fn [_] (reset! counter 0))}]]
           ;; :background-color and :gravity
           
           ;; :hint on EditText
           [:edit-text {:hint "Type something here..."
                        :padding default-padding}]
           ;; :checked and :on-checked-change toggling :visibility
           [:check-box {:text "Show hidden message"
                        :checked false
                        :text-size [16 :sp]
                        :on-checked-change (fn [_ _] (swap! show-message not))}]
           [:text-view {:id ::hidden-message
                        :text "You found the hidden message!"
                        :text-size [16 :sp]
                        :text-color (unchecked-int 0xFF00CC00)
                        :visibility show-message=
                        :padding [16 4 0 4]}]
           ;; :progress on ProgressBar, :on-seek-bar-change on SeekBar
           [:text-view {:id ::seek-label
                        :text seek-text=
                        :text-size [16 :sp]
                        :padding [0 8 0 4]}]
           [:seek-bar {:progress seek=
                       :max 100
                       :layout-width :fill
                       :on-seek-bar-change (fn [_b p _u] (reset! seek-progress p))}]]]
         ;; --- Tab 2: nREPL controls ---
         [:scroll-view {:id ::repl-tab
                        :layout-width :fill
                        :layout-height 0
                        :layout-weight 1
                        :visibility :gone}
          [:linear-layout {:orientation :vertical
                           :padding [32 32 32 32]
                           :layout-width :match-parent}
           [:text-view {:text "nREPL Server"
                        :text-size [24 :sp]
                        :padding [0 0 0 8]}]
           [:text-view {:text "Connect from your editor to live-reload code."
                        :text-size [14 :sp]
                        :text-color (unchecked-int 0xFF888888)
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
                        :padding [0 4 0 0]}]]]])
