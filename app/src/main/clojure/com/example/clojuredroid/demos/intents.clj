(ns com.example.clojuredroid.demos.intents
  "Intents demo: opening URLs and picking files."
  (:require [neko.activity :as act]
            [neko.content :as content]
            [neko.reactive :refer [cell cell=]]
            [neko.resource :refer [get-theme-color]])
  (:import android.app.Activity
           android.content.Intent
           android.net.Uri
           android.view.View))

(def file-info* (cell nil))

(defn format-size [bytes]
  (cond
    (nil? bytes)       "unknown"
    (< bytes 1024)     (str bytes " B")
    (< bytes 1048576)  (format "%.1f KB" (/ (double bytes) 1024))
    :else              (format "%.1f MB" (/ (double bytes) 1048576))))

(defn- pick-file! [^View v]
  (act/start-activity-for-result (.getContext v) Intent/ACTION_OPEN_DOCUMENT
    :type     "*/*"
    :category Intent/CATEGORY_OPENABLE
    :on-result (fn [^Activity act _result-code ^Intent data]
                 (when-let [uri (.getData data)]
                   (reset! file-info*
                           (assoc (content/get-openable-metadata act uri)
                                  :uri (str uri)))))
    :on-cancel (fn [_act]
                 (reset! file-info* :cancelled))))

(defn section-ui
  "Returns the Intents demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors."
  [ctx section-id]
  (let [label-color (get-theme-color ctx :text-color-secondary)
        neko-url    "https://github.com/clj-android/neko"]
    [:scroll-view {:id section-id
                   :layout-width :fill
                   :layout-height :fill
                   :visibility :gone}
     [:linear-layout {:orientation :vertical
                      :padding [16 16 16 16]
                      :layout-width :match-parent}
      ;; Open URL
      [:text-view {:text "Open URL"
                   :text-size [16 :sp]
                   :text-color label-color}]
      [:text-view {:text neko-url
                   :link true
                   :text-size [15 :sp]
                   :padding [0 4 0 12]}]

      ;; File picker
      [:text-view {:text "File Picker"
                   :text-size [16 :sp]
                   :text-color label-color
                   :padding [0 24 0 4]}]
      [:text-view {:text "Pick a file and view its metadata."
                   :text-size [14 :sp]
                   :padding [0 4 0 8]}]
      [:button {:text "Choose File"
                :on-click (fn [^View v] (pick-file! v))}]
      [:text-view {:text (cell= #(let [info @file-info*]
                                    (cond
                                      (nil? info)  ""
                                      (= info :cancelled) "Selection cancelled."
                                      :else
                                      (str "Name: " (:name info)
                                           "\nSize: " (format-size (:size info))
                                           "\nType: " (or (:mime info) "unknown")
                                           "\nURI: " (:uri info)))))
                   :text-size [14 :sp]
                   :padding [0 8 0 0]}]]]))
