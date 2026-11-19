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
    /**
     * pattern = search words joined as a case-insensitive regex, e.g. "java|python" (any word matches),
     * checked against title, company, location and description. "" = no text filter.
     */
    @Query(value = """
            SELECT * FROM jobs j
            WHERE (:pattern = '' OR concat_ws(' ', j.title, j.company, j.location_raw, j.description) ~* :pattern)
              AND (:role = '' OR j.role_tag = :role)
              AND (:city = '' OR j.city_tag = :city)
              AND (:fresherOnly = false OR j.is_fresher = true)
            ORDER BY CASE WHEN :pattern <> '' AND j.title ~* :pattern THEN 0 ELSE 1 END,
                     j.posted_at DESC NULLS LAST, j.id DESC
            """,
           countQuery = """
            SELECT count(*) FROM jobs j
            WHERE (:pattern = '' OR concat_ws(' ', j.title, j.company, j.location_raw, j.description) ~* :pattern)
              AND (:role = '' OR j.role_tag = :role)
              AND (:city = '' OR j.city_tag = :city)
              AND (:fresherOnly = false OR j.is_fresher = true)
            """,
           nativeQuery = true)
    Page<Job> search(@Param("pattern") String pattern, @Param("role") String role, @Param("city") String city,
                     @Param("fresherOnly") boolean fresherOnly, Pageable pageable);

    /** [role, count] for the role dropdown, biggest first. */
    @Query("select j.roleTag, count(j) from Job j where j.roleTag is not null group by j.roleTag order by count(j) desc")
    List<Object[]> countByRole();

    /** [city, count] for the city dropdown, biggest first. */
    @Query("select j.cityTag, count(j) from Job j where j.cityTag is not null group by j.cityTag order by count(j) desc")
    List<Object[]> countByCity();

    long countByIsFresherTrue();

    /**
     * Random but repeatable sample for the accuracy check: the same seed always gives the same jobs,
     * so the prompt v1 vs v2 comparison runs on exactly the same 50 posts.
     */
    @Query(value = "SELECT * FROM jobs WHERE role_tag IS NOT NULL ORDER BY md5(id::text || :seed) LIMIT :size",
           nativeQuery = true)
    List<Job> randomSample(@Param("seed") String seed, @Param("size") int size);

    /** Jobs the LLM has not tagged yet and has not given up on, oldest first. */
    List<Job> findByRoleTagIsNullAndTagAttemptsLessThanOrderByIdAsc(int maxAttempts, Pageable pageable);

    /** Jobs the LLM gave up on: they get the rule-based fallback. */
    List<Job> findByRoleTagIsNullAndTagAttemptsGreaterThanEqual(int maxAttempts);
}
