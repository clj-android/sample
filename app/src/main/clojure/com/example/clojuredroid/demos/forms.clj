(ns com.example.clojuredroid.demos.forms
  "Forms & Input demo: on-text-change, character count, input types."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.resource :refer [get-theme-color]]))

(def text-mirror* (cell ""))
(def char-count* (cell 0))

(defn section-ui
  "Returns the Forms demo section UI tree.
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
    ;; Live text mirror
    [:text-view {:text "Text Mirror (:on-text-change)"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:edit-text {:hint "Type here to see live updates..."
                 :layout-width :fill
                 :on-text-change (fn [text]
                                   (reset! text-mirror* text)
                                   (reset! char-count* (count text)))}]
    [:text-view {:text (cell= #(str "Mirror: " @text-mirror*))
                 :padding [0 4 0 0]}]
    [:text-view {:text (cell= #(str "Characters: " @char-count*))
                 :text-size [13 :sp]
                 :text-color caption-color
                 :padding [0 2 0 16]}]

    ;; Input types
    [:text-view {:text "Input Types (:input-type)"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 8 0 4]}]
    [:edit-text {:hint "Number input (decimal)"
                 :input-type :number
                 :layout-width :fill
                 :padding [12 12 12 12]}]
    [:edit-text {:hint "Integer input"
                 :input-type :integer
                 :layout-width :fill
                 :padding [12 12 12 12]}]
    [:edit-text {:hint "Phone input"
                 :input-type :phone
                 :layout-width :fill
                 :padding [12 12 12 12]}]
    [:edit-text {:hint "Email input"
                 :input-type :email
                 :layout-width :fill
                 :padding [12 12 12 12]}]
    [:edit-text {:hint "Password input"
                 :input-type :password
                 :layout-width :fill
                 :padding [12 12 12 12]}]]]))
