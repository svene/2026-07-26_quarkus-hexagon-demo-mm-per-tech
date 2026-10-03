package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.location.Locations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fills a static page shell ({@code /shells/*.html}): {@code {{nav}}} becomes the location switcher, built from
 * {@link Locations} (the DC is managed on /admin), and every other {@code {{key}}} its value.
 * Values come from the server only (route constants, location names), never from a request.
 */
final class PageShell {

    private record NavItem(String href, String label) {}

    private PageShell() {}

    static String render(String shell, String activeHref, Map<String, String> values) {
        String html = read(shell).replace("{{nav}}", nav(activeHref));
        for (var value : values.entrySet()) {
            html = html.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        return html;
    }

    private static String nav(String activeHref) {
        var items = new ArrayList<NavItem>();
        items.add(new NavItem("/admin", "Admin"));
        Locations.REPLENISHED.forEach(l -> items.add(new NavItem("/locations/" + l.id(), l.name())));
        items.add(new NavItem("/shop", "Shop"));
        return items.stream()
            .map(i -> "<li%s><a href=\"%s\">%s</a></li>".formatted(
                i.href().equals(activeHref) ? " class=\"is-active\"" : "", i.href(), i.label()))
            .collect(Collectors.joining("", "<nav class=\"tabs\" id=\"location-nav\"><ul>", "</ul></nav>"));
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
