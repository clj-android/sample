(ns com.example.clojuredroid.t-hello
  (:require [clojure.test :refer :all]
            [com.example.clojuredroid.hello :as hello]))

(deftest greeting-returns-string
  (is (string? (hello/greeting)))
  (is (= "Hello from Clojure!" (hello/greeting))))
