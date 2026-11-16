package com.beep.jobportal.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One job post. Maps to the "jobs" table created in V1__init.sql. */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ----- from the source -----
    @Column(nullable = false)
    private String source;

    @Column(name = "source_job_id", nullable = false)
    private String sourceJobId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(name = "location_raw")
    private String locationRaw;

    private String description;

    @Column(name = "apply_url")
    private String applyUrl;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(name = "dedupe_key", nullable = false, unique = true)
    private String dedupeKey;

    // ----- from the LLM tagger -----
    @Column(name = "role_tag")
    private String roleTag;

    @Column(name = "city_tag")
    private String cityTag;

    @Column(name = "is_fresher")
    private Boolean isFresher;

    @Column(name = "tagged_at")
    private Instant taggedAt;

    @Column(name = "tag_attempts", nullable = false)
    private int tagAttempts;

    @Column(name = "tag_status")
    private String tagStatus;

    protected Job() {
        // required by JPA
    }

    public Job(String source, String sourceJobId, String title, String company) {
        this.source = source;
        this.sourceJobId = sourceJobId;
        this.title = title;
        this.company = company;
    }

    public Long getId() { return id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceJobId() { return sourceJobId; }
    public void setSourceJobId(String sourceJobId) { this.sourceJobId = sourceJobId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLocationRaw() { return locationRaw; }
    public void setLocationRaw(String locationRaw) { this.locationRaw = locationRaw; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public String getRoleTag() { return roleTag; }
    public void setRoleTag(String roleTag) { this.roleTag = roleTag; }

    public String getCityTag() { return cityTag; }
    public void setCityTag(String cityTag) { this.cityTag = cityTag; }

    public Boolean getIsFresher() { return isFresher; }
    public void setIsFresher(Boolean isFresher) { this.isFresher = isFresher; }

    public Instant getTaggedAt() { return taggedAt; }
    public void setTaggedAt(Instant taggedAt) { this.taggedAt = taggedAt; }

    public int getTagAttempts() { return tagAttempts; }
    public void setTagAttempts(int tagAttempts) { this.tagAttempts = tagAttempts; }

    public String getTagStatus() { return tagStatus; }
    public void setTagStatus(String tagStatus) { this.tagStatus = tagStatus; }
}
