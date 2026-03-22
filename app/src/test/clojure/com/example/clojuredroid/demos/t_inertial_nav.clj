(ns com.example.clojuredroid.demos.t-inertial-nav
  (:require [clojure.test :refer :all]
            [com.example.clojuredroid.demos.inertial-nav :as nav]))

(def ^:private eps 1e-9)

(defn- approx=
  "True if all elements of vectors a and b are within eps."
  [a b]
  (every? #(< (Math/abs (double %)) eps) (map - a b)))

;; ── Quaternion tests ────────────────────────────────────────────────────────

(deftest qnorm-identity
  (is (approx= [1.0 0.0 0.0 0.0] (nav/qnorm [1.0 0.0 0.0 0.0]))))

(deftest qnorm-normalizes
  (let [q (nav/qnorm [2.0 0.0 0.0 0.0])]
    (is (approx= [1.0 0.0 0.0 0.0] q)))
  (let [q (nav/qnorm [1.0 1.0 1.0 1.0])
        expected (let [s (/ 1.0 2.0)] [s s s s])]
    (is (approx= expected q))))

(deftest qnorm-zero-returns-identity
  (is (approx= [1.0 0.0 0.0 0.0] (nav/qnorm [0.0 0.0 0.0 0.0]))))

(deftest qmul-identity
  (let [q [0.7071067811865476 0.7071067811865476 0.0 0.0]]
    (is (approx= q (nav/qmul [1.0 0.0 0.0 0.0] q)))
    (is (approx= q (nav/qmul q [1.0 0.0 0.0 0.0])))))

(deftest qmul-inverse
  (let [q  [0.7071067811865476 0.7071067811865476 0.0 0.0]
        qi [0.7071067811865476 -0.7071067811865476 0.0 0.0]]
    (is (approx= [1.0 0.0 0.0 0.0] (nav/qmul q qi)))))

(deftest qrot-identity
  (is (approx= [1.0 2.0 3.0] (nav/qrot [1.0 0.0 0.0 0.0] [1.0 2.0 3.0]))))

(deftest qrot-90-around-z
  ;; 90 deg around Z: x->y, y->-x
  (let [a (/ Math/PI 4.0)
        q [(Math/cos a) 0.0 0.0 (Math/sin a)]]
    (is (approx= [0.0 1.0 0.0] (nav/qrot q [1.0 0.0 0.0])))))

(deftest rv->q-zero-is-identity
  (is (approx= [1.0 0.0 0.0 0.0] (nav/rv->q [0.0 0.0 0.0]))))

(deftest rv->q-roundtrip
  ;; Small rotation vector → quaternion → rotate → should match axis-angle
  (let [q (nav/rv->q [0.0 0.0 (/ Math/PI 2.0)])]
    (is (approx= [0.0 1.0 0.0] (nav/qrot q [1.0 0.0 0.0])))))

(deftest gravity->q-z-up
  ;; Gravity pointing straight down (device flat, screen up): [0 0 9.8]
  ;; Should produce identity-ish quaternion (already aligned with world Z)
  (let [q (nav/gravity->q [0.0 0.0 9.8])]
    ;; Rotating [0,0,1] by q should give [0,0,1]
    (is (approx= [0.0 0.0 1.0] (nav/qrot q [0.0 0.0 1.0])))))

;; ── Vector ops ──────────────────────────────────────────────────────────────

(deftest vadd-basic
  (is (= [4.0 6.0 8.0] (nav/vadd [1.0 2.0 3.0] [3.0 4.0 5.0]))))

(deftest vsub-basic
  (is (= [-2.0 -2.0 -2.0] (nav/vsub [1.0 2.0 3.0] [3.0 4.0 5.0]))))

(deftest vscale-basic
  (is (= [2.0 4.0 6.0] (nav/vscale [1.0 2.0 3.0] 2.0))))

(deftest vcross-basic
  ;; i × j = k
  (is (= [0.0 0.0 1.0] (nav/vcross [1.0 0.0 0.0] [0.0 1.0 0.0])))
  ;; j × i = -k
  (is (= [0.0 0.0 -1.0] (nav/vcross [0.0 1.0 0.0] [1.0 0.0 0.0]))))

(deftest vmag-basic
  (is (< (Math/abs (- 5.0 (nav/vmag [3.0 4.0 0.0]))) eps))
  (is (< (Math/abs (- 0.0 (nav/vmag [0.0 0.0 0.0]))) eps)))

;; ── Matrix ops ──────────────────────────────────────────────────────────────

(deftest mi-is-identity
  (let [m (nav/mi 3)]
    (is (= 1.0 (nav/mget m 3 0 0)))
    (is (= 1.0 (nav/mget m 3 1 1)))
    (is (= 1.0 (nav/mget m 3 2 2)))
    (is (= 0.0 (nav/mget m 3 0 1)))
    (is (= 0.0 (nav/mget m 3 1 0)))))

(deftest mm-identity
  (let [I (nav/mi 3)
        a (double-array [1 2 3 4 5 6 7 8 9])
        r (nav/mm a I 3)]
    (is (every? #(< (Math/abs (double %)) eps)
                (map - (seq r) (seq a))))))

(deftest mt-transpose
  (let [a (double-array [1 2 3 4 5 6 7 8 9])
        t (nav/mt a 3)]
    (is (= 1.0 (nav/mget t 3 0 0)))
    (is (= 4.0 (nav/mget t 3 0 1)))
    (is (= 2.0 (nav/mget t 3 1 0)))))

(deftest madd-msub
  (let [a (double-array [1 2 3 4])
        b (double-array [5 6 7 8])
        sum (nav/madd a b 2)
        diff (nav/msub a b 2)]
    (is (= [6.0 8.0 10.0 12.0] (vec sum)))
    (is (= [-4.0 -4.0 -4.0 -4.0] (vec diff)))))

(deftest inv3-identity
  (let [I (nav/mi 3)
        Ii (nav/inv3 I)]
    (is (some? Ii))
    (is (every? #(< (Math/abs (double %)) eps)
                (map - (seq Ii) (seq I))))))

(deftest inv3-known
  (let [a (double-array [2 0 0 0 3 0 0 0 4])
        ai (nav/inv3 a)]
    (is (some? ai))
    (is (< (Math/abs (- 0.5 (nav/mget ai 3 0 0))) eps))
    (is (< (Math/abs (- (/ 1.0 3.0) (nav/mget ai 3 1 1))) eps))
    (is (< (Math/abs (- 0.25 (nav/mget ai 3 2 2))) eps))))

(deftest inv3-singular-returns-nil
  (is (nil? (nav/inv3 (nav/mz 3)))))
