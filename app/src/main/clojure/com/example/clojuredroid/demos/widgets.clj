(ns com.example.clojuredroid.demos.widgets
  "Basic widget demos: counter, checkbox, seek-bar, switch, toggle-button,
  radio-group, spinner, image-view, and horizontal-scroll-view."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.resource :refer [get-theme-color]])
  (:import android.widget.ArrayAdapter
           android.widget.Spinner))

(def counter* (cell 0))
(def seek-progress* (cell 50))
(def show-message* (cell false))
(def show-message= (cell= #(if @show-message* :visible :gone)))
(def switch-state* (cell false))
(def toggle-state* (cell false))
(def selected-radio* (cell "None"))
(def spinner-items ["Red" "Green" "Blue" "Yellow" "Purple"])
(def spinner-selection* (cell (first spinner-items)))

(defn section-ui
  "Returns the Widgets demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors.
  `section-id` is a keyword used as the :id for the root view."
  [ctx section-id]
  (let [label-color    (get-theme-color ctx :text-color-secondary)
        caption-color  (get-theme-color ctx :text-color-secondary)]
  [:scroll-view {:id section-id
                 :layout-width :fill
                 :layout-height :fill
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 16]
                    :layout-width :match-parent}
    ;; Counter with reactive cells
    [:text-view {:text "Counter (reactive cells)"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 0 0 4]}]
    [:text-view {:text (cell= #(str "Counter: " @counter*))
                 :text-size [18 :sp]}]
    [:linear-layout {:orientation :horizontal
                     :padding [0 4 0 12]}
     [:button {:text "Increment"
               :on-click (fn [_] (swap! counter* inc))}]
     [:button {:text "Reset"
               :on-click (fn [_] (reset! counter* 0))}]]

    ;; CheckBox toggling visibility
    [:text-view {:text "CheckBox + Visibility"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 4 0 4]}]
    [:check-box {:text "Show hidden message"
                 :checked false
                 :on-checked-change (fn [_ _] (swap! show-message* not))}]
    [:text-view {:text "You found the hidden message!"
                 :text-size [16 :sp]
                 :text-color 0xFF00CC00
                 :visibility show-message=
                 :padding [16 4 0 8]}]

    ;; SeekBar
    [:text-view {:text "SeekBar"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 8 0 4]}]
    [:text-view {:text (cell= #(str "Progress: " @seek-progress* "%"))
                 :text-size [14 :sp]}]
    [:seek-bar {:progress (cell= #(deref seek-progress*))
                :max 100
                :layout-width :fill
                :on-seek-bar-change (fn [_ p _] (reset! seek-progress* p))}]

    ;; Switch
    [:text-view {:text "Switch"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:switch {:text "Enable feature"
              :checked false
              :on-checked-change (fn [_ checked] (reset! switch-state* checked))}]
    [:text-view {:text (cell= #(str "Switch is: " (if @switch-state* "ON" "OFF")))
                 :padding [0 2 0 0]}]

    ;; ToggleButton
    [:text-view {:text "ToggleButton"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:toggle-button {:checked false
                     :on-checked-change (fn [_ checked] (reset! toggle-state* checked))}]
    [:text-view {:text (cell= #(str "Toggle is: " (if @toggle-state* "ON" "OFF")))
                 :padding [0 2 0 0]}]

    ;; RadioGroup
    [:text-view {:text "RadioGroup"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:radio-group {:orientation :vertical}
     [:radio-button {:text "Option A"
                     :on-checked-change (fn [_ c] (when c (reset! selected-radio* "A")))}]
     [:radio-button {:text "Option B"
                     :on-checked-change (fn [_ c] (when c (reset! selected-radio* "B")))}]
     [:radio-button {:text "Option C"
                     :on-checked-change (fn [_ c] (when c (reset! selected-radio* "C")))}]]
    [:text-view {:text (cell= #(str "Selected: " @selected-radio*))
                 :padding [0 2 0 0]}]

    ;; ImageView
    [:text-view {:text "ImageView"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:image-view {:image-resource android.R$drawable/ic_dialog_info
                  :padding [0 4 0 0]}]

    ;; Spinner
    [:text-view {:text "Spinner"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:text-view {:text (cell= #(str "Selected: " @spinner-selection*))
                 :text-size [14 :sp]
                 :text-color caption-color
                 :padding [0 2 0 4]}]
    [:spinner {:id ::spinner
               :on-create (fn [^Spinner spinner]
                            (.setAdapter spinner
                              (ArrayAdapter. (.getContext spinner)
                                android.R$layout/simple_spinner_dropdown_item
                                ^java.util.List (java.util.ArrayList. ^java.util.Collection spinner-items))))
               :on-item-selected (fn [_ _ pos _]
                                   (reset! spinner-selection*
                                           (nth spinner-items pos)))}]

    ;; HorizontalScrollView
    [:text-view {:text "HorizontalScrollView"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 16 0 4]}]
    [:horizontal-scroll-view {:layout-width :fill}
     [:linear-layout {:orientation :horizontal}
      [:button {:text "One"}]
      [:button {:text "Two"}]
      [:button {:text "Three"}]
      [:button {:text "Four"}]
      [:button {:text "Five"}]
      [:button {:text "Six"}]
      [:button {:text "Seven"}]]]]]))
