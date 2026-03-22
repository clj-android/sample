(ns com.example.clojuredroid.demos.inertial-nav
  "Inertial navigation using EKF sensor fusion (Solin et al. 2017).

  Extended Kalman Filter tracking position, velocity, orientation (quaternion),
  and IMU biases. Uses zero-velocity updates (ZUPT) for stationary calibration
  and pseudo-velocity updates to bound drift.

  State: [position(3) velocity(3) quaternion(4) accel-bias(3) gyro-bias(3)]
  Error state: [δp(3) δv(3) δθ(3) δba(3) δbw(3)] — 15 dimensions"
  (:require [neko.reactive :refer [cell cell=]]
            [neko.sensor :as sensor]
            [neko.resource :refer [get-theme-color]]))

;; ── Sensors ──────────────────────────────────────────────────────────────────

(defonce ^:private accel-cell* (atom nil))
(defonce ^:private gyro-cell*  (atom nil))

(defn- ensure-sensors! [ctx]
  (when-not @accel-cell*
    (reset! accel-cell* (sensor/sensor-cell ctx :accelerometer :delay :game)))
  (when-not @gyro-cell*
    (reset! gyro-cell* (sensor/sensor-cell ctx :gyroscope :delay :game))))

;; ── Constants ────────────────────────────────────────────────────────────────

(def ^:private gravity 9.80665)
(def ^:private NE 15)              ; error-state dimension

;; Process noise std-devs
(def ^:private sa  0.5)            ; accel (m/s²)
(def ^:private sw  0.01)           ; gyro (rad/s)
(def ^:private sba 0.001)          ; accel-bias walk
(def ^:private sbw 0.0001)         ; gyro-bias walk

;; ZUPT (zero-velocity update)
(def ^:private zupt-win-ns (* 250 1000000)) ; 250 ms window
(def ^:private zupt-var-thr 0.15)  ; accel-magnitude variance threshold
(def ^:private zupt-r 0.01)        ; ZUPT measurement noise σ

;; Pseudo-velocity constraint
(def ^:private pv-speed 0.75)      ; expected speed (m/s)
(def ^:private pv-var 4.0)         ; variance (σ² = 2² m²/s²)

(def ^:private cal-N 50)           ; calibration samples

;; ── Quaternion [w x y z] ─────────────────────────────────────────────────────

(defn qmul [[w1 x1 y1 z1] [w2 x2 y2 z2]]
  (let [w1 (double w1) x1 (double x1) y1 (double y1) z1 (double z1)
        w2 (double w2) x2 (double x2) y2 (double y2) z2 (double z2)]
    [(- (* w1 w2) (* x1 x2) (* y1 y2) (* z1 z2))
     (+ (* w1 x2) (* x1 w2) (* y1 z2) (- (* z1 y2)))
     (+ (* w1 y2) (- (* x1 z2)) (* y1 w2) (* z1 x2))
     (+ (* w1 z2) (* x1 y2) (- (* y1 x2)) (* z1 w2))]))

(defn qnorm [[w x y z]]
  (let [w (double w) x (double x) y (double y) z (double z)
        n (Math/sqrt (+ (* w w) (* x x) (* y y) (* z z)))]
    (if (> n 1e-10) [(/ w n) (/ x n) (/ y n) (/ z n)] [1.0 0.0 0.0 0.0])))

(defn qrot
  "Rotate vector by quaternion using Rodrigues formula."
  [[qw qx qy qz] [vx vy vz]]
  (let [qw (double qw) qx (double qx) qy (double qy) qz (double qz)
        vx (double vx) vy (double vy) vz (double vz)
        tx (* 2.0 (- (* qy vz) (* qz vy)))
        ty (* 2.0 (- (* qz vx) (* qx vz)))
        tz (* 2.0 (- (* qx vy) (* qy vx)))]
    [(+ vx (* qw tx) (- (* qy tz) (* qz ty)))
     (+ vy (* qw ty) (- (* qz tx) (* qx tz)))
     (+ vz (* qw tz) (- (* qx ty) (* qy tx)))]))

(defn rv->q
  "Rotation vector → quaternion."
  [[tx ty tz]]
  (let [tx (double tx) ty (double ty) tz (double tz)
        a (Math/sqrt (+ (* tx tx) (* ty ty) (* tz tz)))]
    (if (< a 1e-12) [1.0 0.0 0.0 0.0]
      (let [ha (/ a 2.0) s (/ (Math/sin ha) a)]
        (qnorm [(Math/cos ha) (* tx s) (* ty s) (* tz s)])))))

(defn gravity->q
  "Quaternion aligning device gravity reading (at rest) with world Z-up."
  [accel]
  (let [[ax ay az] accel
        ax (double ax) ay (double ay) az (double az)
        m (Math/sqrt (+ (* ax ax) (* ay ay) (* az az)))]
    (if (< m 1e-6) [1.0 0.0 0.0 0.0]
      (let [gx (/ ax m) gy (/ ay m) gz (/ az m)
            cm (Math/sqrt (+ (* gy gy) (* gx gx)))]
        (if (< cm 1e-6)
          (if (> gz 0) [1.0 0.0 0.0 0.0] [0.0 1.0 0.0 0.0])
          (let [ang (Math/acos (max -1.0 (min 1.0 gz)))
                ha (/ ang 2.0) s (/ (Math/sin ha) cm)]
            (qnorm [(Math/cos ha) (* gy s) (* (- gx) s) 0.0])))))))

;; ── 3-vector ops ─────────────────────────────────────────────────────────────

(defn vmag ^double [[x y z]]
  (let [x (double x) y (double y) z (double z)]
    (Math/sqrt (+ (* x x) (* y y) (* z z)))))

(defn vadd [[ax ay az] [bx by bz]]
  [(+ (double ax) (double bx)) (+ (double ay) (double by)) (+ (double az) (double bz))])

(defn vsub [[ax ay az] [bx by bz]]
  [(- (double ax) (double bx)) (- (double ay) (double by)) (- (double az) (double bz))])

(defn vscale [[x y z] ^double s]
  [(* (double x) s) (* (double y) s) (* (double z) s)])

(defn vcross [[ax ay az] [bx by bz]]
  (let [ax (double ax) ay (double ay) az (double az)
        bx (double bx) by (double by) bz (double bz)]
    [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))]))

;; ── N×N matrix ops (flat row-major double[]) ─────────────────────────────────

(defn mz ^doubles [^long n] (double-array (* n n)))

(defn mi ^doubles [^long n]
  (let [^doubles m (mz n)] (dotimes [i n] (aset m (+ (* i n) i) 1.0)) m))

(defn mget ^double [^doubles m ^long n ^long i ^long j]
  (aget m (+ (* i n) j)))

(defn mset
  "Set element (i,j) of n×n matrix. No primitive hints on args (Clojure 4-arg limit)."
  [^doubles m n i j v]
  (aset m (int (+ (* (int n) (int i)) (int j))) (double v)))

(defn mm
  "Multiply two n×n matrices."
  ^doubles [^doubles a ^doubles b ^long n]
  (let [^doubles c (mz n)]
    (dotimes [i n] (dotimes [j n]
      (loop [k 0, s 0.0]
        (if (< k n)
          (recur (inc k) (+ s (* (aget a (+ (* i n) k)) (aget b (+ (* k n) j)))))
          (aset c (+ (* i n) j) s)))))
    c))

(defn mt ^doubles [^doubles m ^long n]
  (let [^doubles t (mz n)]
    (dotimes [i n] (dotimes [j n] (aset t (+ (* j n) i) (aget m (+ (* i n) j)))))
    t))

(defn madd ^doubles [^doubles a ^doubles b ^long n]
  (let [nn (* n n) ^doubles c (double-array nn)]
    (dotimes [i nn] (aset c i (+ (aget a i) (aget b i)))) c))

(defn msub ^doubles [^doubles a ^doubles b ^long n]
  (let [nn (* n n) ^doubles c (double-array nn)]
    (dotimes [i nn] (aset c i (- (aget a i) (aget b i)))) c))

(defn inv3
  "Invert a 3×3 matrix; nil if singular."
  ^doubles [^doubles m]
  (let [a (aget m 0) b (aget m 1) c (aget m 2)
        d (aget m 3) e (aget m 4) f (aget m 5)
        g (aget m 6) h (aget m 7) ii (aget m 8)
        det (+ (* a (- (* e ii) (* f h)))
               (- (* b (- (* d ii) (* f g))))
               (* c (- (* d h) (* e g))))]
    (when (> (Math/abs det) 1e-15)
      (let [r (/ 1.0 det)]
        (double-array
          [(* r (- (* e ii) (* f h)))  (* r (- (* c h) (* b ii))) (* r (- (* b f) (* c e)))
           (* r (- (* f g) (* d ii)))  (* r (- (* a ii) (* c g))) (* r (- (* c d) (* a f)))
           (* r (- (* d h) (* e g)))   (* r (- (* b g) (* a h)))  (* r (- (* a e) (* b d)))])))))

;; ── EKF helpers ──────────────────────────────────────────────────────────────
;;
;; State mean (16): [p₀₋₂  v₃₋₅  q₆₋₉  ba₁₀₋₁₂  bw₁₃₋₁₅]
;; Error state (15): [δp₀₋₂  δv₃₋₅  δθ₆₋₈  δba₉₋₁₁  δbw₁₂₋₁₄]

(defn- apply-dx
  "Apply error-state correction δx to state mean."
  [^doubles mean ^doubles dx]
  (let [m (aclone mean)]
    ;; Position & velocity: additive
    (dotimes [i 3]
      (aset m i       (+ (aget mean i)       (aget dx i)))
      (aset m (+ 3 i) (+ (aget mean (+ 3 i)) (aget dx (+ 3 i)))))
    ;; Quaternion: body-frame multiplicative update q ⊗ δq
    (let [q (qnorm (qmul [(aget mean 6) (aget mean 7) (aget mean 8) (aget mean 9)]
                          (rv->q [(aget dx 6) (aget dx 7) (aget dx 8)])))]
      (dotimes [i 4] (aset m (+ 6 i) (double (q i)))))
    ;; Biases: additive
    (dotimes [i 3]
      (aset m (+ 10 i) (+ (aget mean (+ 10 i)) (aget dx (+ 9 i))))
      (aset m (+ 13 i) (+ (aget mean (+ 13 i)) (aget dx (+ 12 i)))))
    m))

(defn- cov-update
  "Covariance update: P_new = (I - K·H) · P"
  [^doubles P ^doubles KH]
  (mm (msub (mi NE) KH NE) P NE))

;; ── EKF Predict ──────────────────────────────────────────────────────────────

(defn- ekf-predict
  "Propagate state and covariance forward by dt seconds.
  Accelerometer and gyroscope readings are treated as control inputs (Eq. 5-6)."
  [ekf-state a-meas w-meas ^double dt]
  (let [^doubles mean (:mean ekf-state)
        ^doubles cov  (:cov ekf-state)
        ;; Extract current state
        q  [(aget mean 6) (aget mean 7) (aget mean 8) (aget mean 9)]
        ba [(aget mean 10) (aget mean 11) (aget mean 12)]
        bw [(aget mean 13) (aget mean 14) (aget mean 15)]

        ;; Bias-corrected sensor inputs (Eq. 6)
        ac (vsub a-meas ba)
        wc (vsub w-meas bw)

        ;; Rotate corrected accel to world frame, subtract gravity
        aw (qrot q ac)
        al [(aw 0) (aw 1) (- (double (aw 2)) gravity)]

        ;; Propagate state mean (Eq. 5: mechanization equations)
        ^doubles nm (double-array 16)
        _ (dotimes [i 3]
            (aset nm i       (+ (aget mean i) (* (aget mean (+ 3 i)) dt)))  ; p += v·dt
            (aset nm (+ 3 i) (+ (aget mean (+ 3 i)) (* (double (al i)) dt)))) ; v += a_lin·dt
        nq (qnorm (qmul q (rv->q (vscale wc dt))))
        _ (dotimes [i 4] (aset nm (+ 6 i) (double (nq i))))
        _ (dotimes [i 3]
            (aset nm (+ 10 i) (aget mean (+ 10 i)))  ; biases constant (Eq. 7)
            (aset nm (+ 13 i) (aget mean (+ 13 i))))

        ;; Error-state Jacobian F = I + Fc·dt  (NE × NE)
        ;; Body-frame MEKF: δx = [δp δv δθ δba δbw]
        ^doubles F (mi NE)

        ;; ∂δṗ/∂δv = I → F[0:3, 3:6] += I·dt
        _ (dotimes [i 3]
            (mset F NE i (+ 3 i) (+ (mget F NE i (+ 3 i)) dt)))

        ;; ∂δv̇/∂δθ = -[a_world]× · R  (body-frame convention)
        ;; Columns of R via quaternion rotation of basis vectors
        re0 (qrot q [1.0 0.0 0.0])
        re1 (qrot q [0.0 1.0 0.0])
        re2 (qrot q [0.0 0.0 1.0])
        ;; Column j of -[aw]×·R = -(aw × re_j)
        c0 (vcross aw re0) c1 (vcross aw re1) c2 (vcross aw re2)
        _ (dotimes [i 3]
            (mset F NE (+ 3 i) 6 (+ (mget F NE (+ 3 i) 6) (* (- (double (c0 i))) dt)))
            (mset F NE (+ 3 i) 7 (+ (mget F NE (+ 3 i) 7) (* (- (double (c1 i))) dt)))
            (mset F NE (+ 3 i) 8 (+ (mget F NE (+ 3 i) 8) (* (- (double (c2 i))) dt))))

        ;; ∂δv̇/∂δba = -R → F[3:6, 9:12] += -R·dt
        _ (dotimes [i 3]
            (mset F NE (+ 3 i) 9  (+ (mget F NE (+ 3 i) 9)  (* (- (double (re0 i))) dt)))
            (mset F NE (+ 3 i) 10 (+ (mget F NE (+ 3 i) 10) (* (- (double (re1 i))) dt)))
            (mset F NE (+ 3 i) 11 (+ (mget F NE (+ 3 i) 11) (* (- (double (re2 i))) dt))))

        ;; ∂δθ̇/∂δθ = -[wc]× → F[6:9, 6:9] += -[wc]×·dt
        [wcx wcy wcz] wc
        wcx (double wcx) wcy (double wcy) wcz (double wcz)
        _ (do (mset F NE 6 7 (+ (mget F NE 6 7) (* (- wcz) dt)))
              (mset F NE 6 8 (+ (mget F NE 6 8) (* wcy dt)))
              (mset F NE 7 6 (+ (mget F NE 7 6) (* wcz dt)))
              (mset F NE 7 8 (+ (mget F NE 7 8) (* (- wcx) dt)))
              (mset F NE 8 6 (+ (mget F NE 8 6) (* (- wcy) dt)))
              (mset F NE 8 7 (+ (mget F NE 8 7) (* wcx dt))))

        ;; ∂δθ̇/∂δbw = -I → F[6:9, 12:15] += -I·dt
        _ (dotimes [i 3]
            (mset F NE (+ 6 i) (+ 12 i) (+ (mget F NE (+ 6 i) (+ 12 i)) (- dt))))

        ;; Process noise Q (diagonal)
        ^doubles Q (mz NE)
        sa2dt (* sa sa dt) sw2dt (* sw sw dt) sba2dt (* sba sba dt) sbw2dt (* sbw sbw dt)
        _ (dotimes [i 3]
            (mset Q NE (+ 3 i)  (+ 3 i)  sa2dt)     ; velocity
            (mset Q NE (+ 6 i)  (+ 6 i)  sw2dt)     ; orientation
            (mset Q NE (+ 9 i)  (+ 9 i)  sba2dt)    ; accel bias
            (mset Q NE (+ 12 i) (+ 12 i) sbw2dt))   ; gyro bias

        ;; Covariance propagation: P = F·P·Fᵀ + Q
        ^doubles Ft (mt F NE)
        ^doubles new-cov (madd (mm (mm F cov NE) Ft NE) Q NE)]

    {:mean nm :cov new-cov}))

;; ── ZUPT update (Eq. 10) ────────────────────────────────────────────────────

(defn- ekf-zupt
  "Zero-velocity update: measurement v = 0."
  [ekf]
  (let [^doubles mean (:mean ekf)
        ^doubles cov  (:cov ekf)
        ;; S = P[v,v] + R²·I  (3×3)
        ^doubles S (double-array 9)
        _ (dotimes [i 3] (dotimes [j 3]
            (aset S (+ (* i 3) j)
                  (+ (mget cov NE (+ 3 i) (+ 3 j))
                     (if (= i j) (* zupt-r zupt-r) 0.0)))))
        ^doubles Si (inv3 S)]
    (if-not Si ekf
      (let [;; Kalman gain K (NE×3): K[i,j] = Σ_k P[i,3+k]·Si[k,j]
            ^doubles K (double-array (* NE 3))
            _ (dotimes [i NE] (dotimes [j 3]
                (loop [k 0, s 0.0]
                  (if (< k 3)
                    (recur (inc k) (+ s (* (mget cov NE i (+ 3 k))
                                           (aget Si (+ (* k 3) j)))))
                    (aset K (+ (* i 3) j) s)))))
            ;; Innovation = 0 - v
            ^doubles iv (double-array [(- (aget mean 3)) (- (aget mean 4)) (- (aget mean 5))])
            ;; δx = K · innovation
            ^doubles dx (double-array NE)
            _ (dotimes [i NE]
                (loop [j 0, s 0.0]
                  (if (< j 3)
                    (recur (inc j) (+ s (* (aget K (+ (* i 3) j)) (aget iv j))))
                    (aset dx i s))))
            ;; K·H (NE×NE): only columns 3..5 are nonzero
            ^doubles KH (mz NE)
            _ (dotimes [i NE] (dotimes [j 3]
                (mset KH NE i (+ 3 j) (aget K (+ (* i 3) j)))))]
        {:mean (apply-dx mean dx) :cov (cov-update cov KH)}))))

;; ── Pseudo-velocity update (Eq. 11) ─────────────────────────────────────────

(defn- ekf-pseudo-v
  "Pseudo-measurement: speed ≈ 0.75 m/s with large variance.
  Constrains speed to a reasonable range without being overly informative."
  [ekf]
  (let [^doubles mean (:mean ekf)
        ^doubles cov  (:cov ekf)
        vx (aget mean 3) vy (aget mean 4) vz (aget mean 5)
        spd (Math/sqrt (+ (* vx vx) (* vy vy) (* vz vz)))]
    (if (< spd 1e-6) ekf
      (let [;; H (1×NE): ∂‖v‖/∂δv_i = v_i/‖v‖
            h3 (/ vx spd) h4 (/ vy spd) h5 (/ vz spd)
            ;; S = H·P·Hᵀ + R  (scalar)
            S (+ (* h3 (+ (* (mget cov NE 3 3) h3) (* (mget cov NE 3 4) h4) (* (mget cov NE 3 5) h5)))
                 (* h4 (+ (* (mget cov NE 4 3) h3) (* (mget cov NE 4 4) h4) (* (mget cov NE 4 5) h5)))
                 (* h5 (+ (* (mget cov NE 5 3) h3) (* (mget cov NE 5 4) h4) (* (mget cov NE 5 5) h5)))
                 pv-var)]
        (if (< (Math/abs S) 1e-15) ekf
          (let [innov (- pv-speed spd)
                iS (/ 1.0 S)
                ;; P·Hᵀ (NE×1)
                ^doubles PHt (double-array NE)
                _ (dotimes [i NE]
                    (aset PHt i (+ (* (mget cov NE i 3) h3)
                                   (* (mget cov NE i 4) h4)
                                   (* (mget cov NE i 5) h5))))
                ;; δx = P·Hᵀ · innov / S
                ^doubles dx (double-array NE)
                _ (dotimes [i NE] (aset dx i (* (aget PHt i) iS innov)))
                ;; K·H (NE×NE): K[i]·H[j] = PHt[i]·iS·H[j]
                ^doubles KH (mz NE)
                _ (dotimes [i NE]
                    (let [ki (* (aget PHt i) iS)]
                      (mset KH NE i 3 (* ki h3))
                      (mset KH NE i 4 (* ki h4))
                      (mset KH NE i 5 (* ki h5))))]
            {:mean (apply-dx mean dx) :cov (cov-update cov KH)}))))))

;; ── ZUPT detection ───────────────────────────────────────────────────────────

(defn- zupt?
  "Detect stationarity via accelerometer magnitude variance over a rolling window."
  [win]
  (when (>= (count win) 5)
    (let [mags (mapv (fn [[_ ax ay az]]
                       (Math/sqrt (+ (* (double ax) (double ax))
                                     (* (double ay) (double ay))
                                     (* (double az) (double az)))))
                     win)
          n  (count mags)
          mu (/ (reduce + mags) n)
          vr (/ (reduce + (map #(let [d (- (double %) mu)] (* d d)) mags)) n)]
      (< vr zupt-var-thr))))

;; ── Navigation state ─────────────────────────────────────────────────────────

(def ^:private init-nav
  {:ekf nil :zupt-win [] :last-a-ns nil
   :last-w [0.0 0.0 0.0] :total-dist 0.0 :cal-samples []})

(defonce ^:private nav*          (atom init-nav))
(defonce ^:private running?*     (cell false))
(defonce ^:private calibrating?* (cell false))
(defonce ^:private calibrated?*  (atom false))
(defonce ^:private status*       (cell "Press Start to begin"))
(defonce ^:private display*      (cell {:position [0.0 0.0 0.0] :speed 0.0 :total-dist 0.0
                                        :biases {:accel [0.0 0.0 0.0] :gyro [0.0 0.0 0.0]}}))

(defn- init-ekf [q0]
  (let [m (double-array 16)
        P (mz NE)]
    (dotimes [i 4] (aset m (+ 6 i) (double (q0 i))))
    (dotimes [i 3]
      (mset P NE i i 0.01)
      (mset P NE (+ 3 i) (+ 3 i) 0.01)
      (mset P NE (+ 6 i) (+ 6 i) 0.01)
      (mset P NE (+ 9 i) (+ 9 i) 0.1)
      (mset P NE (+ 12 i) (+ 12 i) 0.01))
    {:mean m :cov P}))

(defn- process-cal! [reading]
  (let [{:keys [cal-samples]}
        (swap! nav* update :cal-samples conj reading)]
    (when (>= (count cal-samples) cal-N)
      (let [avg (vscale (reduce vadd cal-samples) (/ 1.0 (double (count cal-samples))))
            now (System/nanoTime)]
        (swap! nav* assoc :ekf (init-ekf (gravity->q avg)) :cal-samples []
               :last-a-ns now :last-w [0.0 0.0 0.0])
        (reset! calibrated?* true)
        (reset! calibrating?* false)
        (reset! status* "Tracking")))))

(defn- trim-win [win ^long now]
  (let [cutoff (- now (long zupt-win-ns))]
    (into [] (filter #(>= (long (% 0)) cutoff)) win)))

(defn- process-a! [reading]
  (let [now (System/nanoTime)
        st  (swap! nav*
              (fn [{:keys [ekf last-a-ns zupt-win last-w total-dist] :as s}]
                (if-not (and ekf last-a-ns)
                  (assoc s :last-a-ns now)
                  (let [dt (/ (double (- now (long last-a-ns))) 1e9)]
                    (if-not (< 0.0 dt 0.5)
                      (assoc s :last-a-ns now)
                      (let [zw   (conj (trim-win zupt-win now)
                                       [now (reading 0) (reading 1) (reading 2)])
                            ekf1 (ekf-predict ekf reading last-w dt)
                            zp   (zupt? zw)
                            ekf2 (if zp (ekf-zupt ekf1) (or (ekf-pseudo-v ekf1) ekf1))
                            vel  [(aget ^doubles (:mean ekf2) 3)
                                  (aget ^doubles (:mean ekf2) 4)
                                  (aget ^doubles (:mean ekf2) 5)]
                            spd  (vmag vel)]
                        (assoc s :ekf ekf2 :zupt-win zw :last-a-ns now
                               :total-dist (+ total-dist (* spd dt)))))))))]
    (when-let [ekf (:ekf st)]
      (let [m ^doubles (:mean ekf)]
        (reset! display* {:position   [(aget m 0) (aget m 1) (aget m 2)]
                          :speed      (vmag [(aget m 3) (aget m 4) (aget m 5)])
                          :total-dist (:total-dist st)
                          :biases     {:accel [(aget m 10) (aget m 11) (aget m 12)]
                                       :gyro  [(aget m 13) (aget m 14) (aget m 15)]}})))))

(defn- process-g! [reading]
  (swap! nav* assoc :last-w reading))

;; ── Sensor watches ───────────────────────────────────────────────────────────

(defonce ^:private watches?* (atom false))

(defn- on-a [_ _ _ v]
  (when v (cond @calibrating?* (process-cal! v) @running?* (process-a! v))))

(defn- on-g [_ _ _ v]
  (when (and v @running?* (not @calibrating?*)) (process-g! v)))

(defn- install-watches! []
  (when (compare-and-set! watches?* false true)
    (when-let [c @accel-cell*] (add-watch c ::ia on-a))
    (when-let [c @gyro-cell*]  (add-watch c ::ig on-g))))

;; ── Controls ─────────────────────────────────────────────────────────────────

(defn- start-pause! []
  (if @running?*
    (do (reset! running?* false)
        (swap! nav* assoc :last-a-ns nil)
        (reset! status* "Paused"))
    (if @calibrated?*
      (do (reset! running?* true) (reset! status* "Tracking"))
      (do (reset! running?* true) (reset! calibrating?* true)
          (swap! nav* assoc :cal-samples [])
          (reset! status* (str "Calibrating (" cal-N " samples)…"))))))

(defn- reset-all! []
  (reset! running?* false)
  (reset! calibrating?* false)
  (reset! calibrated?* false)
  (reset! nav* init-nav)
  (reset! display* {:position [0.0 0.0 0.0] :speed 0.0 :total-dist 0.0
                    :biases {:accel [0.0 0.0 0.0] :gyro [0.0 0.0 0.0]}})
  (reset! status* "Press Start to begin"))

;; ── Section UI ───────────────────────────────────────────────────────────────

(defn section-ui
  "Returns the Inertial Navigation demo section UI tree."
  [ctx section-id]
  (ensure-sensors! ctx)
  (install-watches!)
  (let [lc (get-theme-color ctx :text-color-secondary)
        ok (and @accel-cell* @gyro-cell*)]
    [:scroll-view {:id section-id :layout-width :fill :layout-height :fill :visibility :gone}
     (if-not ok
       [:linear-layout {:orientation :vertical :padding [16 16 16 16] :layout-width :match-parent}
        [:text-view {:text "This demo requires both an accelerometer and a gyroscope."
                     :text-size [16 :sp] :text-color lc}]]
       [:linear-layout {:orientation :vertical :padding [16 16 16 16] :layout-width :match-parent}
        [:text-view {:text "Inertial navigation demo"
                    :text-size [18 :sp]}]
        [:text-view {:text "Probably not accurate enough for any useful purpose"
                    :text-size [14 :sp]}]
        [:linear-layout {:min-height [24 :sp]}]
        ;; Status
        [:text-view {:text (cell= #(deref status*))
                     :text-size [14 :sp] :text-color lc :padding [0 0 0 8]}]

        ;; Buttons
        [:linear-layout {:orientation :horizontal :padding [0 0 0 16]}
         [:button {:text (cell= #(if @running?* "Pause" "Start"))
                   :on-click (fn [_] (start-pause!))}]
         [:button {:text "Reset"
                   :on-click (fn [_] (reset-all!))}]]

        ;; Speed
        [:text-view {:text "Speed" :text-size [16 :sp] :text-color lc}]
        [:text-view {:text (cell= #(format "%.3f m/s" (double (:speed @display*))))
                     :text-size [18 :sp] :padding [0 2 0 12]}]

        ;; Total distance
        [:text-view {:text "Distance traveled" :text-size [16 :sp] :text-color lc}]
        [:text-view {:text (cell= #(format "%.3f m" (double (:total-dist @display*))))
                     :text-size [18 :sp] :padding [0 2 0 12]}]

        ;; Displacement per axis
        [:text-view {:text "Displacement from start" :text-size [16 :sp] :text-color lc}]
        [:text-view {:text (cell= #(let [[x y z] (:position @display*)]
                                     (format "X  %.3f m    Y  %.3f m    Z  %.3f m"
                                             (double x) (double y) (double z))))
                     :text-size [14 :sp] :padding [0 2 0 4]}]
        [:text-view {:text (cell= #(let [[x y z] (:position @display*)]
                                     (format "Net  %.3f m"
                                             (Math/sqrt (+ (* (double x) (double x))
                                                           (* (double y) (double y))
                                                           (* (double z) (double z)))))))
                     :text-size [14 :sp] :padding [0 2 0 12]}]

        ;; Estimated biases
        [:text-view {:text "Estimated sensor biases" :text-size [16 :sp] :text-color lc}]
        [:text-view {:text (cell= #(let [{:keys [accel]} (or (:biases @display*) {})
                                         [x y z] (or accel [0 0 0])]
                                     (format "Accel  %.4f  %.4f  %.4f  m/s²"
                                             (double x) (double y) (double z))))
                     :text-size [12 :sp] :padding [0 2 0 2]}]
        [:text-view {:text (cell= #(let [{:keys [gyro]} (or (:biases @display*) {})
                                         [x y z] (or gyro [0 0 0])]
                                     (format "Gyro   %.5f  %.5f  %.5f  rad/s"
                                             (double x) (double y) (double z))))
                     :text-size [12 :sp] :padding [0 2 0 12]}]

        ;; Note
        [:text-view {:text (str "EKF inertial odometry (Solin et al. 2017). "
                                "Hold phone still briefly for zero-velocity calibration. "
                                "Estimates sensor biases online to reduce drift.")
                     :text-size [12 :sp] :text-color lc :padding [0 8 0 0]}]])]))
