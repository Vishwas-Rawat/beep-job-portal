package com.beep.jobportal.normalize;

import com.beep.jobportal.fetch.RawJob;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the dedupe key: normalized company | title | city.
 * Two posts with the same key are treated as the same job,
 * even if they came from different sources.
 */
@Component
public class JobNormalizer {

    private static final Pattern COMPANY_SUFFIX = Pattern.compile(
            "\\b(private|pvt|limited|ltd|inc|llc|llp|corp|corporation|co)\\b\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final IndiaLocations india;

    public JobNormalizer(IndiaLocations india) {
        this.india = india;
    }

    /** "Paytm Pvt. Ltd." -> "paytm" */
    public String company(String company) {
        String s = COMPANY_SUFFIX.matcher(company == null ? "" : company).replaceAll(" ");
        return squash(s);
    }

    /** "Software Engineer - II (Backend)" -> "software engineer ii backend" */
    public String title(String title) {
        return squash(title == null ? "" : title);
    }

    public String dedupeKey(RawJob job) {
        String city = india.cityOrFallback(job.locationRaw()).toLowerCase(Locale.ROOT);
        return company(job.company()) + "|" + title(job.title()) + "|" + city;
    }

    private static String squash(String s) {
        return NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
