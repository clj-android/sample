(ns com.example.clojuredroid.main-activity
  "Neko UI DSL demo app with DrawerLayout navigation.

  This namespace is automatically loaded by ClojureActivity when
  com.example.clojuredroid.MainActivity is created.

  The app demonstrates neko features across multiple demo sections,
  accessible via a navigation drawer.

  To modify the UI live from the REPL:

    (require '[com.example.clojuredroid.main-activity :as ui])

    ;; Rebuild with current theme colors and reload:
    (ui/rebuild-ui-tree!)

    ;; Or set an arbitrary tree and reload:
    (reset! ui/*ui-tree [...])
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

;; Theme color helper.
;;
;; Reads from @*activity at call time, so colors are always current.
;; Using functions (rather than atoms) means theme changes take effect
;; automatically: when the user toggles dark/light mode, Android recreates
;; the Activity, make-ui resets *activity, and the next rebuild-ui-tree! call
;; gets fresh colors without any explicit invalidation.
(defn- tc [kw] (res/get-theme-color @*activity kw))

;; Guard: when rebuild-ui-tree! does its internal reset!, we don't want the
;; :reload watch to fire (that would cause make-ui → rebuild → watch → loop).
;; External resets (e.g. from the REPL) still trigger reload normally.
(defonce ^:private building? (volatile! false))

(defonce *ui-tree (atom []))

(defn rebuild-ui-tree!
  "Rebuilds *ui-tree from the current theme and section UIs.
  Call from the REPL to pick up theme or UI changes:
    (rebuild-ui-tree!)"
  []
  (vreset! building? true)
  (reset! *ui-tree
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
                       :background-color (tc :color-primary)
                       :padding [4 8 16 8]
                       :insets-padding :top
                       :layout-width :fill
                       :gravity :center-vertical}
       [:button {:text "\u2630"
                 :text-size [22 :sp]
                 :text-color (tc :color-on-primary)
                 :background-color (tc :color-primary)
                 :min-width [48 :dp]
                 :opens-drawer true}]
       [:text-view {:id ::header-title
                    :text "Widgets"
                    :text-size [20 :sp]
                    :text-color (tc :color-on-primary)
                    :padding [4 0 0 0]}]]
      ;; Section container
      [:frame-layout {:layout-width :fill
                      :layout-height 0
                      :layout-weight 1}
       (widgets/section-ui @*activity ::widgets)
       (lists/section-ui @*activity ::lists)
       (material/section-ui @*activity ::material)
       (forms/section-ui @*activity ::forms)
       (dialogs/section-ui @*activity ::dialogs)
       (repl-ui/section-ui @*activity ::repl)]]
     ;; === Drawer (second child, layout-gravity :start) ===
     [:scroll-view {:layout-width [280 :dp]
                    :layout-height :fill
                    :layout-gravity :start
                    :background-color (tc :color-surface)}
      (into [:linear-layout {:orientation :vertical
                             :padding [0 24 0 0]}
             [:text-view {:text "Neko Demos"
                          :text-size [22 :sp]
                          :text-color (tc :color-on-surface)
                          :padding [24 16 24 20]}]
             ;; Divider: on-surface at 12% alpha (Material 2 spec)
             [:view {:background-color (bit-or (bit-and (tc :color-on-surface) 0x00FFFFFF)
                                               0x1F000000)
                     :layout-width :fill
                     :layout-height [1 :dp]}]]
            (map nav-item sections))]])
  (vreset! building? false))

(defn make-ui
  "Builds the UI tree using neko's declarative DSL."
  [^Activity activity]
  (reset! *activity activity)
  (rebuild-ui-tree!)
  (let [root (ui/make-ui activity @*ui-tree)]
    (reset! *root-view root)
    (repl-ui/init! activity root)
    root))

(defn on-create
  "Called automatically by ClojureActivity when the activity is created."
  [^Activity activity saved-instance-state]
  (wini/enable-edge-to-edge! activity)
  (let [^View root (make-ui activity)]
    (.setContentView activity ^View root)))

(defn reload-ui!
  "Hot-reload the UI from the REPL."
  []
  (when-let [activity (ClojureActivity/getInstance
                        "com.example.clojuredroid.main-activity")]
    (.reloadUi ^ClojureActivity activity)))

;; REPL hot-reload: (reset! *ui-tree [...]) triggers a live reload.
;; Internal rebuilds via rebuild-ui-tree! set building? and are ignored.
(add-watch *ui-tree :reload
           (fn [_ _ _ _]
             (when-not @building?
               (repl-ui/sync-status!)
               (reload-ui!))))
