(ns com.example.clojuredroid.repl
  "nREPL server controls and local REPL for the sample app.

  State is held in reactive cells so the UI always reflects the
  true server state, regardless of when the view is built or rebuilt."
  (:require [neko.find-view :refer [find-view]]
            [neko.resource :refer [get-theme-color]]
            [neko.reactive :refer [cell cell=]]
            [neko.threading :refer [on-ui]]
            [clj-android.repl.server :as repl-server])
  (:import [android.graphics Typeface]
           [android.widget EditText ScrollView TextView]))

;; ---------------------------------------------------------------------------
;; State cells (defonce — survive UI rebuilds)
;; ---------------------------------------------------------------------------

;; :status  — :unknown | :starting | :running | :stopping | :stopped | :error | :unavailable
;; :port    — integer when running, else nil
;; :error   — string when :error, else nil
(defonce state* (cell {:status :unknown :port nil :error nil}))

;; Theme colors for the current activity, updated in section-ui.
(defonce theme-colors* (cell nil))

;; Derived formula cells — auto-recompute when state* or theme-colors* changes.
(defonce status-text=
  (cell= #(let [{:keys [status port]} @state*]
            (case status
              :starting    "Starting..."
              :stopping    "Stopping..."
              :running     (str "Running on port " port)
              :stopped     "Stopped"
              :error       "Error"
              :unavailable "Unavailable"
              "Stopped"))))

(defonce status-color=
  (cell= #(let [{:keys [status]}      @state*
                {:keys [secondary
                        error-color]} (or @theme-colors* {})]
            (case status
              :running  0xFF00CC00
              :starting 0xFFCCCC00
              :stopping 0xFFCCCC00
              (:error :unavailable) (or error-color 0xFFFF4444)
              (or secondary 0xFF888888)))))

(defonce error-text=
  (cell= #(or (:error @state*) "")))

(defonce start-enabled=
  (cell= #(contains? #{:stopped :error} (:status @state*))))

(defonce stop-enabled=
  (cell= #(= :running (:status @state*))))

;; ---------------------------------------------------------------------------
;; Local REPL state
;; ---------------------------------------------------------------------------

(defonce repl-output* (cell ""))
(defonce repl-evaluating?* (cell false))

(defonce eval-btn-enabled=
  (cell= #(and (not @repl-evaluating?*) (= :running (:status @state*)))))

(defonce repl-hint=
  (cell= #(if (= :running (:status @state*))
            "Type Clojure code here..."
            "Start nREPL server first")))

;; ---------------------------------------------------------------------------
;; Root-view reference (for reading the port input field)
;; ---------------------------------------------------------------------------

(defonce root-view* (atom nil))

;; ---------------------------------------------------------------------------
;; Auto-start watcher (defonce — starts once at namespace load)
;; ---------------------------------------------------------------------------

(defonce ^:private _auto-start-watcher
  (future
    (cond
      ;; Server already running: autoStartNrepl may have completed before
      ;; this namespace loaded.  Reflect reality immediately.
      (repl-server/running?)
      (reset! state* {:status :running
                      :port   (or (repl-server/port) 7888)
                      :error  nil})

      ;; nREPL infrastructure not bundled (release build or runtime-repl
      ;; excluded).  Nothing will start it.
      (not (repl-server/repl-available?))
      (reset! state* {:status :unavailable :port nil :error nil})

      ;; Wait for autoStartNrepl to bring the server up.
      :else
      (do
        (reset! state* {:status :starting :port nil :error nil})
        (if (repl-server/wait-for-ready :timeout-ms 60000)
          (reset! state* {:status :running
                          :port   (or (repl-server/port) 7888)
                          :error  nil})
          (when-not (repl-server/running?)
            (reset! state* {:status :stopped :port nil :error nil})))))))

;; ---------------------------------------------------------------------------
;; Init (called from make-ui after each view rebuild)
;; ---------------------------------------------------------------------------

(defn init!
  "Stores the new root-view for port-input access."
  [_activity root-view]
  (reset! root-view* root-view))

;; ---------------------------------------------------------------------------
;; Button handlers
;; ---------------------------------------------------------------------------

(defn- parse-port [^EditText et]
  (try
    (let [p (Integer/parseInt (.. et getText toString trim))]
      (when (<= 1 p 65535) p))
    (catch NumberFormatException _ nil)))

(defn- on-start-nrepl [_view]
  (when-let [root @root-view*]
    (let [port (some-> (find-view root ::nrepl-port-input) parse-port)]
      (if-not port
        (swap! state* assoc :error "Invalid port (1\u201365535)")
        (do
          (reset! state* {:status :starting :port nil :error nil})
          (.start
            (Thread.
              (.getThreadGroup (Thread/currentThread))
              (fn []
                (try
                  (repl-server/start port)
                  (reset! state* {:status :running
                                  :port   (or (repl-server/port) port)
                                  :error  nil})
                  (catch Throwable t
                    (reset! state* {:status :error
                                    :port   nil
                                    :error  (.getMessage t)}))))
              "nrepl-start"
              1048576)))))))

(defn- on-stop-nrepl [_view]
  (reset! state* {:status :stopping :port nil :error nil})
  (future
    (try
      (repl-server/stop)
      (reset! state* {:status :stopped :port nil :error nil})
      (catch Throwable t
        (reset! state* {:status :error
                        :port   nil
                        :error  (.getMessage t)})))))

;; ---------------------------------------------------------------------------
;; Local REPL eval
;; ---------------------------------------------------------------------------

(defn- append-output! [text]
  (swap! repl-output* str text))

(defn- scroll-to-bottom! []
  (when-let [root @root-view*]
    (when-let [^ScrollView sv (find-view root ::repl-scroll)]
      (on-ui (.post sv (fn [] (.fullScroll sv android.view.View/FOCUS_DOWN)))))))

(defn- on-eval [_view]
  (when-let [root @root-view*]
    (when-let [^EditText input (find-view root ::repl-input)]
      (let [code (.. input getText toString trim)]
        (when (seq code)
          (append-output! (str "=> " code "\n"))
          (reset! repl-evaluating?* true)
          (future
            (let [result (try
                           (let [out (java.io.StringWriter.)
                                 err (java.io.StringWriter.)
                                 val (binding [*out* out *err* err]
                                       (load-string code))
                                 out-str (str out)
                                 err-str (str err)]
                             (str (when (seq out-str) out-str)
                                  (when (seq err-str) (str "stderr: " err-str "\n"))
                                  (pr-str val) "\n"))
                           (catch Throwable t
                             (str "Error: " (.getMessage t) "\n")))]
              (append-output! result)
              (reset! repl-evaluating?* false)
              (scroll-to-bottom!)))
          (on-ui (.setText input "")))))))

(defn- on-clear-output [_view]
  (reset! repl-output* ""))

;; ---------------------------------------------------------------------------
;; Section UI
;; ---------------------------------------------------------------------------

(defn section-ui
  "Returns the nREPL controls section UI tree.
  Also updates theme-colors* so derived cells use current theme."
  [ctx section-id]
  (reset! theme-colors*
          {:secondary   (get-theme-color ctx :text-color-secondary)
           :error-color (get-theme-color ctx :color-error)})
  (let [subtitle-color (get-theme-color ctx :text-color-secondary)]
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
                   :text-color subtitle-color
                   :padding [0 0 0 16]}]
      [:linear-layout {:orientation :horizontal}
       [:text-view {:text "Port: "
                    :text-size [16 :sp]
                    :padding [0 8 8 0]}]
       [:edit-text {:id ::nrepl-port-input
                    :text "7888"
                    :input-type :integer}]]
      [:text-view {:id ::nrepl-status
                   :text status-text=
                   :text-size [16 :sp]
                   :text-color status-color=
                   :padding [0 4 0 4]}]
      [:linear-layout {:orientation :horizontal
                       :padding [0 4 0 4]}
       [:button {:id ::nrepl-start-btn
                 :text "Start"
                 :enabled start-enabled=
                 :on-click on-start-nrepl}]
       [:button {:id ::nrepl-stop-btn
                 :text "Stop"
                 :enabled stop-enabled=
                 :on-click on-stop-nrepl}]]
      [:text-view {:id ::nrepl-error
                   :text error-text=
                   :text-size [14 :sp]
                   :text-color status-color=
                   :padding [0 4 0 0]}]
      ;; --- Local REPL ---
      [:view {:layout-width :fill
              :layout-height 1
              :padding [0 12 0 0]
              :on-create (fn [^android.view.View v]
                           (.setBackgroundColor v (unchecked-int 0xFF444444)))}]
      [:text-view {:text "Local REPL"
                   :text-size [22 :sp]
                   :padding [0 12 0 8]}]
      [:scroll-view {:id ::repl-scroll
                     :layout-width :fill
                     :layout-height 400
                     :on-create (fn [^ScrollView v]
                                  (.setBackgroundColor v (unchecked-int 0xFF1A1A2E)))}
       [:text-view {:id ::repl-output
                    :text repl-output*
                    :text-size [13 :sp]
                    :text-color (unchecked-int 0xFFE0E0E0)
                    :padding [8 8 8 8]
                    :layout-width :fill
                    :on-create (fn [^TextView tv]
                                 (.setTypeface tv Typeface/MONOSPACE))}]]
      [:edit-text {:id ::repl-input
                   :hint repl-hint=
                   :layout-width :fill
                   :enabled eval-btn-enabled=
                   :on-create (fn [^EditText et]
                                (.setTypeface et Typeface/MONOSPACE)
                                (.setTextSize et 14)
                                (.setMinLines et 2))}]
      [:linear-layout {:orientation :horizontal
                       :padding [0 4 0 16]}
       [:button {:text "Eval"
                 :enabled eval-btn-enabled=
                 :on-click on-eval}]
       [:button {:text "Clear"
                 :on-click on-clear-output}]]]]))
