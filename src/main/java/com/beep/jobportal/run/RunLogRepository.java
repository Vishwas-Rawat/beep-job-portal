package com.beep.jobportal.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunLogRepository extends JpaRepository<RunLog, Long> {

    /** Latest successful run, used for the "last updated" time on the page. */
    Optional<RunLog> findFirstByStatusOrderByFinishedAtDesc(String status);

    /** Latest run of any status, to check on a run that is still going. */
    Optional<RunLog> findFirstByOrderByStartedAtDesc();
}
