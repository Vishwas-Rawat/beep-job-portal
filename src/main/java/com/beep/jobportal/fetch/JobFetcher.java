package com.beep.jobportal.fetch;

import java.util.List;

/**
 * One implementation per job source. Adding a new source = one new class
 * annotated with @Component; RefreshService picks it up automatically.
 */
public interface JobFetcher {

    /** Short id stored in jobs.source, e.g. "greenhouse". */
    String source();

    /** Fetch India jobs from this source. Must not throw for one bad company. */
    List<RawJob> fetch();
}
