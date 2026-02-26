(ns com.example.clojuredroid.ui
  "Neko UI DSL demo. Constructs a declarative UI and provides
  functions for REPL-driven hot-reload.

  To modify the UI live from the REPL:

    (reset! *ui-tree
      [:linear-layout {:id-holder true
                       :orientation :vertical
                       :padding [32 32 32 32]}
       [:button {:text \"New button!\"}]])
    (reload-ui!)"
  (:require [neko.ui :as ui]
            [neko.find-view :refer [find-view]]
            [neko.log :as log])
  (:import android.app.Activity
           android.widget.TextView))

;; Atom holding the current Activity instance. Set by make-ui
;; so that REPL callers can reload the UI without passing it explicitly.
(defonce *activity (atom nil))

;; Root view returned by make-ui (the id-holder linear-layout).
;; Button click handlers use this with find-view to locate child views.
(defonce *root-view (atom nil))

(def ^:private counter (atom 0))

(defonce *ui-tree (atom nil))

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
         [:text-view {:text "Modify this UI live via nREPL!"
                      :text-size [14 :sp]
                      :padding [0 16 0 0]}]])

(defn make-ui
  "Builds the sample UI tree using neko's declarative DSL.
  Reads the UI tree from *ui-tree-fn if set, otherwise uses the default."
  [^Activity activity]
  (reset! *activity activity)
  (let [tree @*ui-tree
        root (ui/make-ui activity tree)]
    (reset! *root-view root)
    root))

(defn reload-ui!
  "Hot-reload the UI from the REPL. Delegates to Java for reliable
  UI-thread dispatch."
  []
  (-> (Class/forName "com.example.clojuredroid.MainActivity")
      (.getMethod "reloadUi" (into-array Class []))
      (.invoke nil (into-array Object []))))

(add-watch *ui-tree :ui-change-watch
           (fn [_key _ref _old _new]
             (reload-ui!)))
