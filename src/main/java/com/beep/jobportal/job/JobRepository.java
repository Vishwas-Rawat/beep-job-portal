package com.beep.jobportal.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface JobRepository extends JpaRepository<Job, Long> {

    /** Loaded once per run so dedupe is an in-memory lookup, not 2,000 DB round trips. */
    @Query("select j.dedupeKey from Job j")
    Set<String> findAllDedupeKeys();

    /** "source|sourceJobId" of every stored job. */
    @Query("select concat(j.source, '|', j.sourceJobId) from Job j")
    Set<String> findAllSourceIds();

    /**
     * Text search on title/company/location. q is never null ("" matches all),
     * which avoids Postgres "could not determine data type" errors on null params.
     */
    @Query("""
            select j from Job j
            where lower(j.title) like lower(concat('%', :q, '%'))
               or lower(j.company) like lower(concat('%', :q, '%'))
               or lower(j.locationRaw) like lower(concat('%', :q, '%'))
            """)
    Page<Job> search(@Param("q") String q, Pageable pageable);

    /** Jobs the LLM has not tagged yet (and has not given up on). */
    List<Job> findByRoleTagIsNullAndTagAttemptsLessThan(int maxAttempts);
}
