package com.example.clojuredroid;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import static org.junit.Assert.fail;

/**
 * JUnit bridge that runs Clojure test namespaces under Robolectric.
 *
 * clojure.test output prints to stdout; Gradle's testLogging config
 * ensures it appears in the console on failure.
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

        // Run tests with output going to stdout (captured by Gradle).
        // Return only the fail+error count as a plain number.
        StringBuilder sb = new StringBuilder();
        sb.append("(let [result (clojure.test/run-tests");
        for (String ns : TEST_NAMESPACES) {
            sb.append(" '").append(ns);
        }
        sb.append(")] (+ (:fail result) (:error result)))");

        Object failCount = eval.invoke(readString.invoke(sb.toString()));
        long fails = ((Number) failCount).longValue();
        if (fails > 0) {
            fail(fails + " clojure.test failure(s) — see test output above");
        }
    }
}
