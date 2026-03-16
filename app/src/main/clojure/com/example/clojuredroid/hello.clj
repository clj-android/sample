(ns com.example.clojuredroid.hello
  "Minimal AOT-compiled namespace used by TestActivity to validate
  that Clojure namespace loading and function calls work on Android.")

(defn greeting
  "Returns a greeting string. Called from TestActivity test 5."
  []
  "Hello from Clojure!")
