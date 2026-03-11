(ns com.example.clojuredroid.main-activity
  "Neko UI DSL demo app with DrawerLayout navigation.

  This namespace is automatically loaded by ClojureActivity when
  com.example.clojuredroid.MainActivity is created.

  The app demonstrates neko features across multiple demo sections,
  accessible via a navigation drawer.

  To modify the UI live from the REPL:

    (require '[com.example.clojuredroid.main-activity :as ui])
    (ui/reload-ui!)"
  (:require [neko.ui :as ui]
            [neko.ui.support.drawer-layout]
            [neko.ui.support.material]
            [neko.ui.support.window-insets :as wini]
            [neko.resource :as res]
            [com.example.clojuredroid.demos.widgets :as widgets]
            [com.example.clojuredroid.demos.lists :as lists]
            [com.example.clojuredroid.demos.material :as material]
            [com.example.clojuredroid.demos.forms :as forms]
            [com.example.clojuredroid.demos.dialogs :as dialogs]
            [com.example.clojuredroid.repl :as repl-ui])
  (:import android.app.Activity
           android.view.View
           com.goodanser.clj_android.runtime.ClojureActivity))

(defonce *activity (atom nil))
(defonce *root-view (atom nil))

;; Section definitions: [keyword label section-fn]
(def ^:private sections
  [[::widgets  "Widgets"             widgets/section-ui]
   [::lists    "Lists & Adapters"    lists/section-ui]
   [::material "Material Components" material/section-ui]
   [::forms    "Forms & Input"       forms/section-ui]
   [::dialogs  "Dialogs & Toasts"    dialogs/section-ui]
   [::repl     "nREPL"               repl-ui/section-ui]])

(defn- drawer-content-spec []
  (vec (mapcat (fn [[id label _]] [label id]) sections)))

(defn- nav-item
  "Returns a UI tree for a single navigation drawer item."
  [[section-id label _]]
  [:text-view {:text label
               :text-size [16 :sp]
               :padding [24 14 24 14]
               :nav-for section-id}])

(defonce *ui-tree (atom []))

(defn- build-ui-tree
  "Builds the full UI tree using theme colors from the given Activity."
  [^Activity activity]
  (let [primary         (res/get-theme-color activity :color-primary)
        on-primary      (res/get-theme-color activity :color-on-primary)
        surface         (res/get-theme-color activity :color-surface)
        on-surface      (res/get-theme-color activity :color-on-surface)
        ;; Divider: on-surface color at 12% alpha (Material 2 spec for dividers)
        divider-color   (bit-or (bit-and (res/get-theme-color activity :color-on-surface) 0x00FFFFFF)
                                0x1F000000)]
    [:drawer-layout {:id ::drawer
                     :id-holder true
                     :layout-width :fill
                     :layout-height :fill
                     :drawer-content (drawer-content-spec)
                     :drawer-title-id ::header-title}
     ;; === Content (first child) ===
     [:linear-layout {:orientation :vertical
                      :layout-width :fill
                      :layout-height :fill}
      [:linear-layout {:id ::header
                       :orientation :horizontal
                       :background-color primary
                       :padding [4 8 16 8]
                       :insets-padding :top
                       :layout-width :fill
                       :gravity :center-vertical}
       [:button {:text "\u2630"
                 :text-size [22 :sp]
                 :text-color on-primary
                 :background-color primary
                 :min-width [48 :dp]
                 :opens-drawer true}]
       [:text-view {:id ::header-title
                    :text "Widgets"
                    :text-size [20 :sp]
                    :text-color on-primary
                    :padding [4 0 0 0]}]]
      ;; Section container
      [:frame-layout {:layout-width :fill
                      :layout-height 0
                      :layout-weight 1}
       (widgets/section-ui activity ::widgets)
       (lists/section-ui activity ::lists)
       (material/section-ui activity ::material)
       (forms/section-ui activity ::forms)
       (dialogs/section-ui activity ::dialogs)
       (repl-ui/section-ui activity ::repl)]]
     ;; === Drawer (second child, layout-gravity :start) ===
     [:scroll-view {:layout-width [280 :dp]
                    :layout-height :fill
                    :layout-gravity :start
                    :background-color surface}
      (into [:linear-layout {:orientation :vertical
                             :padding [0 24 0 0]}
             [:text-view {:text "Neko Demos"
                          :text-size [22 :sp]
                          :text-color on-surface
                          :padding [24 16 24 20]}]
             [:view {:background-color divider-color
                     :layout-width :fill
                     :layout-height [1 :dp]}]]
            (map nav-item sections))]]))

(defn make-ui
  "Builds the UI tree using neko's declarative DSL."
  [^Activity activity]
  (reset! *activity activity)
  (let [tree (build-ui-tree activity)
        root (ui/make-ui activity tree)]
    (reset! *root-view root)
    root))

(defn on-create
  "Called automatically by ClojureActivity when the activity is created."
  [^Activity activity saved-instance-state]
  (wini/enable-edge-to-edge! activity)
  (let [^View root (make-ui activity)]
    (.setContentView activity ^View root)
    (repl-ui/init! activity root)
    (widgets/init! root activity)))

(defn reload-ui!
  "Hot-reload the UI from the REPL."
  []
  (when-let [activity (ClojureActivity/getInstance
                        "com.example.clojuredroid.main-activity")]
    (.reloadUi ^ClojureActivity activity)))

;; Sync nREPL status when UI reloads
(add-watch *ui-tree :nrepl-status-watch
           (fn [_key _ref _old _new]
             (repl-ui/sync-status!)))

(add-watch *ui-tree :nrepl-status-watch
           (fn [_key _ref _old _new]
             (reload-ui!)))
