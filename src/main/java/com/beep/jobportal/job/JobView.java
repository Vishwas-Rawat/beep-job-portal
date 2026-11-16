package com.beep.jobportal.job;

import java.time.Instant;

/** What the API returns for one job (no internal fields like dedupe_key). */
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
        String source
) {
    public static JobView of(Job j) {
        return new JobView(j.getId(), j.getTitle(), j.getCompany(), j.getLocationRaw(),
                j.getCityTag(), j.getRoleTag(), j.getIsFresher(), j.getApplyUrl(),
                j.getPostedAt(), j.getSource());
    }
}
