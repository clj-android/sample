(ns com.example.clojuredroid.demos.intents
  "Intents demo: opening URLs and picking files."
  (:require [neko.activity :as act]
            [neko.reactive :refer [cell cell=]]
            [neko.resource :refer [get-theme-color]])
  (:import android.app.Activity
           android.content.Intent
           android.net.Uri
           android.provider.OpenableColumns
           android.view.View))

(def file-info* (cell nil))

(defn- format-size [bytes]
  (cond
    (nil? bytes)       "unknown"
    (< bytes 1024)     (str bytes " B")
    (< bytes 1048576)  (format "%.1f KB" (/ (double bytes) 1024))
    :else              (format "%.1f MB" (/ (double bytes) 1048576))))

(defn- query-file-metadata
  "Queries the ContentResolver for display name, size, and MIME type."
  [^Activity activity ^Uri uri]
  (let [resolver (.getContentResolver activity)
        mime     (.getType resolver uri)
        cursor   (.query resolver uri nil nil nil nil)]
    (try
      (when (and cursor (.moveToFirst cursor))
        (let [name-idx (.getColumnIndex cursor OpenableColumns/DISPLAY_NAME)
              size-idx (.getColumnIndex cursor OpenableColumns/SIZE)
              name     (when (>= name-idx 0) (.getString cursor name-idx))
              size     (when (>= size-idx 0) (.getLong cursor size-idx))]
          {:name name
           :size size
           :mime mime
           :uri  (str uri)}))
      (finally
        (when cursor (.close cursor))))))

(defn- open-url! [^View v url]
  (act/start-activity (.getContext v) Intent/ACTION_VIEW
    :data (Uri/parse url)))

(defn- pick-file! [^View v]
  (act/start-activity-for-result (.getContext v) Intent/ACTION_OPEN_DOCUMENT
    :type     "*/*"
    :category Intent/CATEGORY_OPENABLE
    :on-result (fn [^Activity act _result-code ^Intent data]
                 (when-let [uri (.getData data)]
                   (reset! file-info* (query-file-metadata act uri))))
    :on-cancel (fn [_act]
                 (reset! file-info* :cancelled))))

(defn section-ui
  "Returns the Intents demo section UI tree.
  `ctx` is an Android Context used to resolve theme colors."
  [ctx section-id]
  (let [label-color (get-theme-color ctx :text-color-secondary)
        neko-url    "https://github.com/niclasberg/neko"]
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
      [:text-view {:text (str "Opens " neko-url " in the default browser.")
                   :text-size [14 :sp]
                   :padding [0 4 0 8]}]
      [:button {:text "Open Neko on GitHub"
                :on-click (fn [^View v] (open-url! v neko-url))}]

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
