(ns com.example.clojuredroid.demos.lists
  "Lists & Adapters demo: RecyclerView with declarative :recycler-items trait."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.ui.support.recycler-view]
            [neko.ui.support.adapters]
            [neko.resource :refer [get-theme-color]]))

(def items* (atom ["Apple" "Banana" "Cherry" "Date" "Elderberry"]))
(def item-count= (cell= #(str (count @items*) " items")))
(def next-num* (atom 5))

(defn section-ui
  "Returns the Lists demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors."
  [ctx section-id]
  (let [label-color   (get-theme-color ctx :text-color-secondary)
        caption-color (get-theme-color ctx :text-color-secondary)]
  [:linear-layout {:id section-id
                   :orientation :vertical
                   :layout-width :fill
                   :layout-height :fill
                   :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 0]
                    :layout-width :match-parent}
    [:text-view {:text "RecyclerView with :recycler-items"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:text-view {:text item-count=
                 :text-size [14 :sp]
                 :text-color caption-color
                 :padding [0 2 0 4]}]
    [:linear-layout {:orientation :horizontal
                     :padding [0 4 0 8]}
     [:button {:text "Add Item"
               :on-click (fn [_]
                           (swap! next-num* inc)
                           (swap! items* conj (str "Item " @next-num*)))}]
     [:button {:text "Remove Last"
               :on-click (fn [_]
                           (when (seq @items*)
                             (swap! items* (comp vec butlast))))}]
     [:button {:text "Reset"
               :on-click (fn [_]
                           (reset! next-num* 5)
                           (reset! items* ["Apple" "Banana" "Cherry"
                                          "Date" "Elderberry"]))}]]]
   ;; RecyclerView fills remaining space (no outer ScrollView needed)
   [:recycler-view {:layout-manager :linear
                    :layout-width :fill
                    :layout-height 0
                    :layout-weight 1
                    :items items*
                    :item-view (fn [data pos]
                                 [:linear-layout {:orientation :horizontal
                                                  :padding [16 12 16 12]
                                                  :layout-width :match-parent}
                                  [:text-view {:text (str (inc pos) ".")
                                               :text-size [16 :sp]
                                               :text-color caption-color
                                               :min-width [32 :dp]}]
                                  [:text-view {:text (str data)
                                               :text-size [16 :sp]
                                               :padding [8 0 0 0]}]])}]]))
