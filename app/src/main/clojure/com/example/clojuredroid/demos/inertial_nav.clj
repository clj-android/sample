(ns com.example.clojuredroid.demos.inertial-nav
  "Inertial navigation demo using accelerometer + gyroscope sensor fusion.
  Tracks displacement, distance traveled, and speed.

  Sensor fusion approach:
  1. On Start, calibrate by averaging accelerometer to find gravity direction
  2. Use gyroscope to maintain rotation matrix (device→world orientation)
  3. Complementary filter corrects gyro drift toward accelerometer gravity
  4. Transform accelerometer to world frame, subtract gravity → linear accel
  5. Integrate: acceleration → velocity → position"
  (:require [neko.reactive :refer [cell cell=]]
            [neko.sensor :as sensor]
            [neko.resource :refer [get-theme-color]]))

;; ---------------------------------------------------------------------------
;; Sensor cells (lazily initialized)
;; ---------------------------------------------------------------------------

(defonce ^:private accel-cell* (atom nil))
(defonce ^:private gyro-cell*  (atom nil))

(defn- ensure-sensors! [ctx]
  (when-not @accel-cell*
    (reset! accel-cell* (sensor/sensor-cell ctx :accelerometer :delay :game)))
  (when-not @gyro-cell*
    (reset! gyro-cell* (sensor/sensor-cell ctx :gyroscope :delay :game))))

;; ---------------------------------------------------------------------------
;; State
;; ---------------------------------------------------------------------------

(def ^:private initial-nav
  {:rotation      [1.0 0.0 0.0  0.0 1.0 0.0  0.0 0.0 1.0] ; row-major 3x3
   :gravity-mag   9.81
   :velocity      [0.0 0.0 0.0]
   :position      [0.0 0.0 0.0]
   :total-dist    0.0
   :last-accel-ns nil
   :last-gyro-ns  nil
   :cal-samples   []})

(defonce ^:private nav* (atom initial-nav))
(defonce ^:private running?* (cell false))
(defonce ^:private calibrating?* (cell false))
(defonce ^:private calibrated?* (atom false))
(defonce ^:private status* (cell "Press Start to begin"))
(defonce ^:private display* (cell {:position  [0.0 0.0 0.0]
                                   :speed     0.0
                                   :total-dist 0.0}))

;; ---------------------------------------------------------------------------
;; Vector math (3-vectors as Clojure vectors)
;; ---------------------------------------------------------------------------

(defn- vmag ^double [v]
  (Math/sqrt (+ (* (double (v 0)) (double (v 0)))
                (* (double (v 1)) (double (v 1)))
                (* (double (v 2)) (double (v 2))))))

(defn- vscale [v ^double s]
  [(* (double (v 0)) s) (* (double (v 1)) s) (* (double (v 2)) s)])

(defn- vadd [a b]
  [(+ (double (a 0)) (double (b 0)))
   (+ (double (a 1)) (double (b 1)))
   (+ (double (a 2)) (double (b 2)))])

(defn- vnorm [v]
  (let [m (vmag v)]
    (if (> m 1e-9) (vscale v (/ 1.0 m)) v)))

(defn- vcross [a b]
  [(- (* (double (a 1)) (double (b 2))) (* (double (a 2)) (double (b 1))))
   (- (* (double (a 2)) (double (b 0))) (* (double (a 0)) (double (b 2))))
   (- (* (double (a 0)) (double (b 1))) (* (double (a 1)) (double (b 0))))])

;; ---------------------------------------------------------------------------
;; 3x3 matrix math (row-major flat vector)
;; ---------------------------------------------------------------------------

(defn- mat*mat
  "Multiply two 3x3 matrices stored as row-major flat vectors."
  [a b]
  (let [^doubles a (double-array a)
        ^doubles b (double-array b)
        ^doubles c (double-array 9)]
    (dotimes [i 3]
      (dotimes [j 3]
        (aset c (+ (* i 3) j)
              (+ (* (aget a (+ (* i 3) 0)) (aget b j))
                 (* (aget a (+ (* i 3) 1)) (aget b (+ 3 j)))
                 (* (aget a (+ (* i 3) 2)) (aget b (+ 6 j)))))))
    (vec c)))

(defn- mat*vec
  "Multiply 3x3 row-major matrix by 3-vector."
  [m v]
  (let [^doubles m (double-array m)
        ^doubles v (double-array v)]
    [(+ (* (aget m 0) (aget v 0)) (* (aget m 1) (aget v 1)) (* (aget m 2) (aget v 2)))
     (+ (* (aget m 3) (aget v 0)) (* (aget m 4) (aget v 1)) (* (aget m 5) (aget v 2)))
     (+ (* (aget m 6) (aget v 0)) (* (aget m 7) (aget v 1)) (* (aget m 8) (aget v 2)))]))

;; ---------------------------------------------------------------------------
;; Rotation helpers
;; ---------------------------------------------------------------------------

(defn- rotation-from-gravity
  "Build device→world rotation matrix (Z-up) from the average accelerometer
  reading at rest. At rest, the accelerometer reads the reaction to gravity,
  which points 'up' in device frame."
  [accel-avg]
  (let [row2 (vnorm accel-avg) ; world-Z (up) in device frame
        seed (if (> (Math/abs (double (row2 0))) 0.9)
               [0.0 1.0 0.0]
               [1.0 0.0 0.0])
        row0 (vnorm (vcross seed row2))
        row1 (vcross row2 row0)]
    [(row0 0) (row0 1) (row0 2)
     (row1 0) (row1 1) (row1 2)
     (row2 0) (row2 1) (row2 2)]))

(defn- gyro-update-rotation
  "Update rotation matrix from gyroscope [wx wy wz] over dt seconds.
  First-order approximation: R_new = R * (I + skew(omega)*dt)."
  [R [wx wy wz] ^double dt]
  (let [wxdt (* (double wx) dt)
        wydt (* (double wy) dt)
        wzdt (* (double wz) dt)]
    (mat*mat R [1.0      (- wzdt)  wydt
                wzdt     1.0       (- wxdt)
                (- wydt) wxdt      1.0])))

(defn- tilt-correction
  "Complementary-filter: nudge R so its gravity prediction aligns with the
  measured accelerometer direction. Only applied when accel magnitude is
  close to gravity (device not under significant linear acceleration)."
  [R accel-dev ^double gravity-mag]
  (let [amag (vmag accel-dev)]
    (if (< (Math/abs (- amag gravity-mag)) (* 0.4 gravity-mag))
      (let [alpha 0.02
            ;; Predicted accel at rest in device frame = R^T * [0 0 g]
            ;; = g * third-row-of-R  (indices 6,7,8)
            pred [(* gravity-mag (double (R 6)))
                  (* gravity-mag (double (R 7)))
                  (* gravity-mag (double (R 8)))]
            err  (vcross (vnorm pred) (vnorm accel-dev))
            cx   (* alpha (double (err 0)))
            cy   (* alpha (double (err 1)))
            cz   (* alpha (double (err 2)))]
        (mat*mat [1.0    (- cz)  cy
                  cz     1.0     (- cx)
                  (- cy) cx      1.0]
                 R))
      R)))

;; ---------------------------------------------------------------------------
;; Calibration (~1 s of samples to determine gravity direction)
;; ---------------------------------------------------------------------------

(def ^:private cal-count 50)

(defn- process-cal-sample! [reading]
  (let [{:keys [cal-samples]}
        (swap! nav* update :cal-samples conj reading)]
    (when (>= (count cal-samples) cal-count)
      (let [avg  (vscale (reduce vadd cal-samples)
                         (/ 1.0 (double (count cal-samples))))
            gmag (vmag avg)
            rot  (rotation-from-gravity avg)
            now  (System/nanoTime)]
        (swap! nav* assoc
               :rotation rot :gravity-mag gmag :cal-samples []
               :last-accel-ns now :last-gyro-ns now)
        (reset! calibrated?* true)
        (reset! calibrating?* false)
        (reset! status* "Tracking")))))

;; ---------------------------------------------------------------------------
;; Integration
;; ---------------------------------------------------------------------------

(def ^:private accel-threshold 0.15) ; m/s², below this is noise
(def ^:private velocity-tau    5.0)  ; seconds, exponential decay time constant

(defn- integrate-accel! [reading]
  (let [now (System/nanoTime)
        new-state
        (swap! nav*
               (fn [{:keys [rotation gravity-mag velocity position
                            total-dist last-accel-ns] :as st}]
                 (if-not last-accel-ns
                   (assoc st :last-accel-ns now)
                   (let [dt (/ (double (- now last-accel-ns)) 1e9)]
                     (if-not (< 0.0 dt 0.5)
                       (assoc st :last-accel-ns now)
                       (let [;; Transform accel to world frame
                             aw   (mat*vec rotation reading)
                             ;; Subtract gravity: at rest world-frame accel = [0 0 g]
                             alin [(aw 0)
                                   (aw 1)
                                   (- (double (aw 2)) gravity-mag)]
                             ;; Threshold small values as noise
                             aflt (mapv #(if (< (Math/abs (double %))
                                                accel-threshold)
                                           0.0 %)
                                        alin)
                             ;; Integrate velocity with exponential decay
                             decay (Math/exp (/ (- dt) velocity-tau))
                             nvel  (vscale (vadd velocity (vscale aflt dt)) decay)
                             spd   (vmag nvel)
                             ;; Integrate position
                             npos  (vadd position (vscale nvel dt))
                             ndist (+ total-dist (* spd dt))
                             ;; Complementary tilt correction
                             nrot  (tilt-correction rotation reading gravity-mag)]
                         (assoc st
                                :rotation nrot :velocity nvel :position npos
                                :total-dist ndist :last-accel-ns now)))))))]
    ;; Update display cell (drives reactive UI)
    (reset! display* {:position   (:position new-state)
                      :speed      (vmag (:velocity new-state))
                      :total-dist (:total-dist new-state)})))

(defn- integrate-gyro! [reading]
  (let [now (System/nanoTime)]
    (swap! nav*
           (fn [{:keys [rotation last-gyro-ns] :as st}]
             (if-not last-gyro-ns
               (assoc st :last-gyro-ns now)
               (let [dt (/ (double (- now last-gyro-ns)) 1e9)]
                 (if-not (< 0.0 dt 0.5)
                   (assoc st :last-gyro-ns now)
                   (assoc st
                          :rotation (gyro-update-rotation rotation reading dt)
                          :last-gyro-ns now))))))))

;; ---------------------------------------------------------------------------
;; Sensor watches (installed once per process)
;; ---------------------------------------------------------------------------

(defonce ^:private watches-installed?* (atom false))

(defn- on-accel [_ _ _ v]
  (when v
    (cond
      @calibrating?* (process-cal-sample! v)
      @running?*     (integrate-accel! v))))

(defn- on-gyro [_ _ _ v]
  (when (and v @running?* (not @calibrating?*))
    (integrate-gyro! v)))

(defn- install-watches! []
  (when (compare-and-set! watches-installed?* false true)
    (when-let [ac @accel-cell*] (add-watch ac ::inav-accel on-accel))
    (when-let [gc @gyro-cell*]  (add-watch gc ::inav-gyro  on-gyro))))

;; ---------------------------------------------------------------------------
;; Button handlers
;; ---------------------------------------------------------------------------

(defn- start-pause! []
  (if @running?*
    ;; Pause — clear timestamps so resume doesn't see a huge dt
    (do (reset! running?* false)
        (swap! nav* assoc :last-accel-ns nil :last-gyro-ns nil)
        (reset! status* "Paused"))
    ;; Start or resume
    (if @calibrated?*
      (do (reset! running?* true)
          (reset! status* "Tracking"))
      (do (reset! running?* true)
          (reset! calibrating?* true)
          (swap! nav* assoc :cal-samples [])
          (reset! status* (str "Calibrating (" cal-count " samples)…"))))))

(defn- reset-all! []
  (reset! running?* false)
  (reset! calibrating?* false)
  (reset! calibrated?* false)
  (reset! nav* initial-nav)
  (reset! display* {:position [0.0 0.0 0.0] :speed 0.0 :total-dist 0.0})
  (reset! status* "Press Start to begin"))

;; ---------------------------------------------------------------------------
;; Section UI
;; ---------------------------------------------------------------------------

(defn section-ui
  "Returns the Inertial Navigation demo section UI tree."
  [ctx section-id]
  (ensure-sensors! ctx)
  (install-watches!)
  (let [lc         (get-theme-color ctx :text-color-secondary)
        sensors-ok (and @accel-cell* @gyro-cell*)]
    [:scroll-view {:id section-id
                   :layout-width :fill
                   :layout-height :fill
                   :visibility :gone}
     (if-not sensors-ok
       ;; Missing hardware fallback
       [:linear-layout {:orientation :vertical
                        :padding [16 16 16 16]
                        :layout-width :match-parent}
        [:text-view {:text "This demo requires both an accelerometer and a gyroscope."
                     :text-size [16 :sp]
                     :text-color lc}]]
       ;; Main UI
       [:linear-layout {:orientation :vertical
                        :padding [16 16 16 16]
                        :layout-width :match-parent}

        ;; Status
        [:text-view {:text (cell= #(deref status*))
                     :text-size [14 :sp]
                     :text-color lc
                     :padding [0 0 0 8]}]

        ;; Buttons
        [:linear-layout {:orientation :horizontal
                         :padding [0 0 0 16]}
         [:button {:text (cell= #(if @running?* "Pause" "Start"))
                   :on-click (fn [_] (start-pause!))}]
         [:button {:text "Reset"
                   :on-click (fn [_] (reset-all!))}]]

        ;; Speed
        [:text-view {:text "Speed"
                     :text-size [16 :sp]
                     :text-color lc}]
        [:text-view {:text (cell= #(format "%.3f m/s" (double (:speed @display*))))
                     :text-size [18 :sp]
                     :padding [0 2 0 12]}]

        ;; Total distance
        [:text-view {:text "Distance traveled"
                     :text-size [16 :sp]
                     :text-color lc}]
        [:text-view {:text (cell= #(format "%.3f m" (double (:total-dist @display*))))
                     :text-size [18 :sp]
                     :padding [0 2 0 12]}]

        ;; Displacement per axis
        [:text-view {:text "Displacement from start"
                     :text-size [16 :sp]
                     :text-color lc}]
        [:text-view {:text (cell= #(let [[x y z] (:position @display*)]
                                      (format "X  %.3f m    Y  %.3f m    Z  %.3f m"
                                              (double x) (double y) (double z))))
                     :text-size [14 :sp]
                     :padding [0 2 0 4]}]
        [:text-view {:text (cell= #(let [[x y z] (:position @display*)]
                                      (format "Net  %.3f m"
                                              (Math/sqrt
                                                (+ (* (double x) (double x))
                                                   (* (double y) (double y))
                                                   (* (double z) (double z)))))))
                     :text-size [14 :sp]
                     :padding [0 2 0 12]}]

        ;; Note
        [:text-view {:text (str "Fuses accelerometer + gyroscope via complementary filter. "
                                "Calibrates gravity direction on start (~1 s). "
                                "Velocity decays with τ=" (int velocity-tau) " s to limit drift.")
                     :text-size [12 :sp]
                     :text-color lc
                     :padding [0 8 0 0]}]])]))
