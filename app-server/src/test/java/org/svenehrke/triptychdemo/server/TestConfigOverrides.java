package org.svenehrke.triptychdemo.server;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config values a test switches at runtime, instead of a {@code @TestProfile} that restarts Quarkus. Only works for
 * properties the app looks up per use ({@code @ConfigProperty Supplier<T>}), not for ones it reads once at startup.
 * Set in {@code @BeforeEach}, {@link #clear()} in {@code @AfterEach}, so the other tests keep the {@code %test} values.
 * Registered in {@code META-INF/services}; ranks above application.properties.
 */
public class TestConfigOverrides implements ConfigSource {

    private static final Map<String, String> VALUES = new ConcurrentHashMap<>();

    public static void set(String name, String value) {
        VALUES.put(name, value);
    }

    public static void clear() {
        VALUES.clear();
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.copyOf(VALUES);
    }

    @Override
    public Set<String> getPropertyNames() {
        return Set.copyOf(VALUES.keySet());
    }

    @Override
    public String getValue(String propertyName) {
        return VALUES.get(propertyName);
    }

    @Override
    public String getName() {
        return "TestConfigOverrides";
    }

    @Override
    public int getOrdinal() {
        return 500;
    }
}
