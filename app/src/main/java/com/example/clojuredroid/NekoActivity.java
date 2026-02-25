package com.example.clojuredroid;

import com.goodanser.clj_android.runtime.ClojureActivity;

/**
 * Activity that demonstrates neko UI DSL for declarative Android UI.
 * The UI is defined entirely in Clojure and can be hot-reloaded via nREPL.
 *
 * <p>By convention, ClojureActivity maps this class to the Clojure namespace
 * {@code com.example.clojuredroid.neko-activity}.</p>
 */
public class NekoActivity extends ClojureActivity {
    // All behavior is defined in the Clojure namespace
    // com.example.clojuredroid.neko-activity
    //
    // The namespace defines:
    //   (defn on-create [activity saved-instance-state] ...)
    //   (defn make-ui [activity] ...)  ;; for reloadUi support
}
