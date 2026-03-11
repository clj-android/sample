(ns com.example.clojuredroid.demos.material
  "Material Components demo: Toolbar with menu, CardView, and FAB."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.ui.support.toolbar]
            [neko.ui.support.material]
            [neko.ui.support.card-view]
            [neko.resource :refer [get-theme-color]]))

(def last-action* (cell "None"))

(defn section-ui
  "Returns the Material Components demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors."
  [ctx section-id]
  (let [label-color   (get-theme-color ctx :text-color-secondary)
        caption-color (get-theme-color ctx :text-color-secondary)]
  [:scroll-view {:id section-id
                 :layout-width :fill
                 :layout-height :fill
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 16]
                    :layout-width :match-parent}
    ;; Toolbar with menu
    [:text-view {:text "Toolbar with :menu trait"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 0 0 4]}]
    [:toolbar {:toolbar-title "Sample Toolbar"
               :toolbar-subtitle "Tap overflow menu \u2192"
               :layout-width :fill
               :menu [{:id ::search :title "Search"
                       :show-as-action :if-room}
                      {:id ::refresh :title "Refresh"}
                      {:id ::settings :title "Settings"}]
               :on-menu-item-click (fn [id]
                                     (reset! last-action*
                                             (str "Menu: " (name id))))}]
    [:text-view {:text (cell= #(str "Last action: " @last-action*))
                 :padding [0 8 0 16]}]

    ;; CardView
    [:text-view {:text "CardView"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 8 0 4]}]
    [:card-view {:card-elevation [4 :dp]
                 :card-corner-radius [8 :dp]
                 :layout-width :fill}
     [:linear-layout {:orientation :vertical
                      :padding [16 16 16 16]}
      [:text-view {:text "Card Title"
                   :text-size [18 :sp]}]
      [:text-view {:text "Content inside a CardView with elevation and rounded corners."
                   :text-size [14 :sp]
                   :text-color label-color
                   :padding [0 4 0 0]}]]]

    [:card-view {:card-elevation [2 :dp]
                 :card-corner-radius [12 :dp]
                 :card-background-color 0xFFF3E5F5
                 :layout-width :fill
                 :layout-margin [0 12 0 0]}
     [:linear-layout {:orientation :vertical
                      :padding [16 16 16 16]}
      [:text-view {:text "Colored Card"
                   :text-size [18 :sp]
                   :text-color 0xFF6A1B9A}]
      [:text-view {:text "Cards can have custom background colors and corner radii."
                   :text-size [14 :sp]
                   :text-color 0xFF888888
                   :padding [0 4 0 0]}]]]

    ;; FloatingActionButton
    [:text-view {:text "FloatingActionButton"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 20 0 4]}]
    [:floating-action-button {:fab-size :normal
                              :on-click (fn [_]
                                          (reset! last-action* "FAB clicked!"))}]
    [:text-view {:text "Tap the FAB above"
                 :text-size [12 :sp]
                 :text-color caption-color
                 :padding [0 4 0 0]}]]]))
