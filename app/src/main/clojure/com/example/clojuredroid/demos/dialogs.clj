(ns com.example.clojuredroid.demos.dialogs
  "Dialogs & Toasts demo: AlertDialog and Toast."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.dialog :as dialog]
            [neko.notify :as notify]
            [neko.resource :refer [get-theme-color]])
  (:import android.content.DialogInterface
           android.view.View))

(def dialog-result* (cell "None"))

(defn section-ui
  "Returns the Dialogs & Toasts demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors."
  [ctx section-id]
  (let [label-color (get-theme-color ctx :text-color-secondary)]
  [:scroll-view {:id section-id
                 :layout-width :fill
                 :layout-height :fill
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 16]
                    :layout-width :match-parent}
    ;; Toast
    [:text-view {:text "Toast"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:linear-layout {:orientation :horizontal
                     :padding [0 4 0 12]}
     [:button {:text "Short Toast"
               :on-click (fn [^View v]
                           (notify/toast (.getContext v) "Hello from Neko!" :short))}]
     [:button {:text "Long Toast"
               :on-click (fn [^View v]
                           (notify/toast (.getContext v) "This toast lasts longer." :long))}]]

    ;; AlertDialog
    [:text-view {:text "AlertDialog"
                 :text-size [16 :sp]
                 :text-color label-color
                 :padding [0 4 0 4]}]
    [:button {:text "Simple Dialog"
              :on-click (fn [^View v]
                          (dialog/alert (.getContext v)
                            {:title "Hello"
                             :message "This is a simple AlertDialog built with neko.dialog/alert."
                             :positive-button ["OK" (fn [_ _]
                                                      (reset! dialog-result* "OK pressed"))]
                             :negative-button ["Cancel" (fn [_ _]
                                                          (reset! dialog-result* "Cancel pressed"))]}))}]
    [:button {:text "List Dialog"
              :on-click (fn [^View v]
                          (let [fruits ["Apple" "Banana" "Cherry" "Date"]]
                            (dialog/alert (.getContext v)
                              {:title "Pick a fruit"
                               :items fruits
                               :on-item-click (fn [d pos]
                                                (reset! dialog-result*
                                                        (str "Picked: " (nth fruits pos)))
                                                (.dismiss ^DialogInterface d))})))}]
    [:button {:text "Cancelable Dialog"
              :on-click (fn [^View v]
                          (dialog/alert (.getContext v)
                            {:title "Can you dismiss me?"
                             :message "Tap outside or press back to cancel."
                             :cancelable true
                             :on-cancel (fn [_]
                                          (reset! dialog-result* "Dialog cancelled"))
                             :positive-button ["Done" (fn [_ _]
                                                        (reset! dialog-result* "Done pressed"))]}))}]
    [:text-view {:text (cell= #(str "Result: " @dialog-result*))
                 :text-size [15 :sp]
                 :padding [0 8 0 0]}]]]))
