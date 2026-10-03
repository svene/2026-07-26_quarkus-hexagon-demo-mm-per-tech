package org.svenehrke.triptychdemo.cross;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Fills a static page shell ({@code /shells/*.html}): every {@code {{key}}} becomes its value.
 * Values come from the server only (route constants, location names), never from a request.
 */
final class PageShell {

    private PageShell() {}

    static String render(String shell, Map<String, String> values) {
        String html = read(shell);
        for (var value : values.entrySet()) {
            html = html.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        return html;
    }

    private static String read(String shell) {
        try (var in = PageShell.class.getResourceAsStream(shell)) {
            if (in == null) throw new IllegalStateException("missing shell: " + shell);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
