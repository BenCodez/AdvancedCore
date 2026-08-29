package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.jupiter.api.Test;

class JavascriptEngineConcurrencyTest {

    @Test
    void sharedEngineBindingsAndEvaluationAreAtomic() throws Exception {
        LockCheckingScriptEngine engine = new LockCheckingScriptEngine();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> first = executor.submit(() -> {
                start.await();
                return JavascriptEngine.evaluateWithBindings(engine, "value", Map.of("value", "first"));
            });
            Future<Object> second = executor.submit(() -> {
                start.await();
                return JavascriptEngine.evaluateWithBindings(engine, "value", Map.of("value", "second"));
            });

            start.countDown();
            assertEquals("first", first.get(2, TimeUnit.SECONDS));
            assertEquals("second", second.get(2, TimeUnit.SECONDS));
            assertTrue(engine.allEvaluationsHeldLock.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class LockCheckingScriptEngine extends AbstractScriptEngine {
        private final AtomicBoolean allEvaluationsHeldLock = new AtomicBoolean(true);

        @Override
        public Object eval(String script, ScriptContext context) {
            if (!Thread.holdsLock(this)) {
                allEvaluationsHeldLock.set(false);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
            return context.getAttribute(script, ScriptContext.ENGINE_SCOPE);
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) throws ScriptException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bindings createBindings() {
            return new SimpleBindings();
        }

        @Override
        public ScriptEngineFactory getFactory() {
            return null;
        }
    }
}
