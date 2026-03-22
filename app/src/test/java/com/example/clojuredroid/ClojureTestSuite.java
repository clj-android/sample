package com.example.clojuredroid;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import static org.junit.Assert.assertEquals;

/**
 * JUnit bridge that runs Clojure test namespaces under Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ClojureTestSuite {

    private static final String[] TEST_NAMESPACES = {
        "com.example.clojuredroid.t-hello",
        "com.example.clojuredroid.demos.t-intents",
        "com.example.clojuredroid.demos.t-inertial-nav",
    };

    @Test
    public void runAllClojureTests() {
        IFn require = Clojure.var("clojure.core", "require");
        IFn symbol = Clojure.var("clojure.core", "symbol");

        require.invoke(symbol.invoke("clojure.test"));

        for (String ns : TEST_NAMESPACES) {
            try {
                require.invoke(symbol.invoke(ns));
            } catch (Exception e) {
                throw new RuntimeException("Failed to require " + ns, e);
            }
        }

        IFn eval = Clojure.var("clojure.core", "eval");
        IFn readString = Clojure.var("clojure.core", "read-string");

        StringBuilder sb = new StringBuilder();
        sb.append("(let [sw (java.io.StringWriter.)");
        sb.append("      result (binding [*out* sw] (clojure.test/run-tests");
        for (String ns : TEST_NAMESPACES) {
            sb.append(" '").append(ns);
        }
        sb.append("))");
        sb.append("      output (str sw)");
        sb.append("      fails (+ (:fail result) (:error result))]");
        sb.append("  (when (pos? fails) (println output))");
        sb.append("  fails)");

        Object failCount = eval.invoke(readString.invoke(sb.toString()));
        assertEquals("Clojure test failures/errors", 0L, ((Number) failCount).longValue());
    }
}
