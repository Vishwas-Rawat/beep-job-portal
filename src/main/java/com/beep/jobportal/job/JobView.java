package com.beep.jobportal.job;

import java.time.Instant;

/**
 * What the API returns for one job (no internal fields like dedupe_key).
 * descriptionMatch: text around the search word when it was found only in the description, else null.
 */
public record JobView(
        Long id,
        String title,
        String company,
        String location,
        String city,
        String role,
        Boolean isFresher,
        String applyUrl,
        Instant postedAt,
        String source,
        String descriptionMatch
) {
    public static JobView of(Job j) {
        return of(j, null);
    }

    public static JobView of(Job j, String descriptionMatch) {
        return new JobView(j.getId(), j.getTitle(), j.getCompany(), j.getLocationRaw(),
                j.getCityTag(), j.getRoleTag(), j.getIsFresher(), j.getApplyUrl(),
                j.getPostedAt(), j.getSource(), descriptionMatch);
    }
}
