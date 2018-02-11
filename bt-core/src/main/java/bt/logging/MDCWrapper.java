package bt.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Oleg Ermolaev Date: 11.02.2018 18:35
 */
public class MDCWrapper {
    public static final String REMOTE_ADDRESS = "remoteAddress";

    private final Map<String, Object> context;

    public MDCWrapper() {
        context = new HashMap<>();
    }

    public MDCWrapper putRemoteAddress(Object value) {
        return put(REMOTE_ADDRESS, value);
    }

    public MDCWrapper put(String key, Object value) {
        context.put(key, value);
        return this;
    }

    public <U> U supply(Supplier<U> supplier) {
        final Map<String, String> current = MDC.getCopyOfContextMap();
        try {
            updateMDC();
            return supplier.get();
        } finally {
            MDC.setContextMap(current);
        }
    }

    public void run(Runnable runnable) {
        final Map<String, String> current = MDC.getCopyOfContextMap();
        try {
            updateMDC();
            runnable.run();
        } finally {
            MDC.setContextMap(current);
        }
    }

    private void updateMDC() {
        context.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, String.valueOf(value));
            }
        });
    }
}
