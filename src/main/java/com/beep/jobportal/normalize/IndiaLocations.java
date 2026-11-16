package com.beep.jobportal.normalize;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knows Indian city names (from cities.txt).
 * Used to (1) keep only India jobs and (2) build the city part of the dedupe key.
 * The LLM later produces the final city_tag; this is the cheap rule-based first pass.
 */
@Component
public class IndiaLocations {

    // \b so "Indianapolis" does NOT count as India
    private static final Pattern INDIA = Pattern.compile("\\bindia\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOTE =
            Pattern.compile("\\b(remote|work from home|wfh)\\b", Pattern.CASE_INSENSITIVE);

    private record Alias(String canonical, Pattern pattern) {}

    private final List<Alias> aliases = new ArrayList<>();

    public IndiaLocations() throws IOException {
        var file = new ClassPathResource("cities.txt");
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 2);
                String canonical = parts[0].strip();
                for (String alias : parts[1].split(",")) {
                    aliases.add(new Alias(canonical,
                            Pattern.compile("\\b" + Pattern.quote(alias.strip()) + "\\b", Pattern.CASE_INSENSITIVE)));
                }
            }
        }
    }

    public boolean isIndia(String location) {
        if (location == null || location.isBlank()) return false;
        return INDIA.matcher(location).find() || firstCity(location) != null;
    }

    /** The Indian city that appears earliest in the text, or null. */
    public String firstCity(String location) {
        if (location == null) return null;
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Alias alias : aliases) {
            Matcher m = alias.pattern().matcher(location);
            if (m.find() && m.start() < bestIndex) {
                bestIndex = m.start();
                best = alias.canonical();
            }
        }
        return best;
    }

    /** City for the dedupe key: a real city, else "Remote", else "India". */
    public String cityOrFallback(String location) {
        String city = firstCity(location);
        if (city != null) return city;
        if (location != null && REMOTE.matcher(location).find()) return "Remote";
        return "India";
    }
}
