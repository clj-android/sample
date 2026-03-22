(ns com.example.clojuredroid.demos.t-intents
  (:require [clojure.test :refer :all]
            [com.example.clojuredroid.demos.intents :as intents]))

(deftest format-size-nil
  (is (= "unknown" (intents/format-size nil))))

(deftest format-size-bytes
  (is (= "0 B" (intents/format-size 0)))
  (is (= "512 B" (intents/format-size 512)))
  (is (= "1023 B" (intents/format-size 1023))))

(deftest format-size-kilobytes
  (is (= "1.0 KB" (intents/format-size 1024)))
  (is (= "5.5 KB" (intents/format-size 5632)))
  (is (= "1024.0 KB" (intents/format-size 1048575))))

(deftest format-size-megabytes
  (is (= "1.0 MB" (intents/format-size 1048576)))
  (is (= "10.0 MB" (intents/format-size 10485760))))
