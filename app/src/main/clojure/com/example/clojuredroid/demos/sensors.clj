(ns com.example.clojuredroid.demos.sensors
  "Sensors demo: reactive cells backed by Android hardware sensors."
  (:require [neko.reactive :refer [cell cell=]]
            [neko.sensor :as sensor]
            [neko.resource :refer [get-theme-color]]))

;; Sensor cells, initialized lazily on first section-ui call.
;; defonce ensures they survive UI rebuilds without re-registering.
(defonce ^:private accel* (atom nil))
(defonce ^:private gyro*  (atom nil))
(defonce ^:private light* (atom nil))

(defn- ensure-sensors! [ctx]
  (when-not @accel* (reset! accel* (sensor/sensor-cell ctx :accelerometer)))
  (when-not @gyro*  (reset! gyro*  (sensor/sensor-cell ctx :gyroscope)))
  (when-not @light* (reset! light* (sensor/sensor-cell ctx :light))))

;; ---------------------------------------------------------------------------
;; Formatting helpers
;; ---------------------------------------------------------------------------

(defn- fmt1 [cell label]
  (if cell
    (cell= #(let [[v] (or @cell [nil])]
              (if v (format "%s: %.2f" label (float v)) (str label ": —"))))
    (str label ": unavailable")))

(defn- fmt3 [cell label]
  (if cell
    (cell= #(let [[x y z] (or @cell [nil nil nil])]
              (if x
                (format "%s  x=%.2f  y=%.2f  z=%.2f"
                        label (float x) (float y) (float z))
                (str label "  —"))))
    (str label ": unavailable")))

;; ---------------------------------------------------------------------------
;; Section UI
;; ---------------------------------------------------------------------------

(defn section-ui
  "Returns the Sensors demo section UI tree."
  [ctx section-id]
  (ensure-sensors! ctx)
  (let [label-color   (get-theme-color ctx :text-color-secondary)
        caption-color (get-theme-color ctx :text-color-secondary)]
  [:scroll-view {:id section-id
                 :layout-width :fill
                 :layout-height :fill
                 :visibility :gone}
   [:linear-layout {:orientation :vertical
                    :padding [16 16 16 16]
                    :layout-width :match-parent}

    ;; Accelerometer
    [:text-view {:text "Accelerometer (m/s²)"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:text-view {:text (fmt3 @accel* "accel")
                 :text-size [14 :sp]
                 :padding [0 2 0 12]}]

    ;; Gyroscope
    [:text-view {:text "Gyroscope (rad/s)"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:text-view {:text (fmt3 @gyro* "gyro")
                 :text-size [14 :sp]
                 :padding [0 2 0 12]}]

    ;; Light
    [:text-view {:text "Light sensor (lux)"
                 :text-size [16 :sp]
                 :text-color label-color}]
    [:text-view {:text (fmt1 @light* "lux")
                 :text-size [14 :sp]
                 :padding [0 2 0 12]}]

    ;; Note
    [:text-view {:text "Values update in real time as the sensor fires."
                 :text-size [12 :sp]
                 :text-color caption-color
                 :padding [0 8 0 0]}]]]))
