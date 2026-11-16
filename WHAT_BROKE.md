# What broke (problem → cause → fix)

Real problems hit while building, logged as they happened.

## Day 1 — Mon 16 Nov 2026

### 1. Maven would not run
- **Problem:** `mvn` failed with "JAVA_HOME environment variable is not defined correctly".
- **Cause:** `JAVA_HOME` pointed to a JDK 21 folder that no longer existed. Only JDK 17 was installed.
- **Fix:** Targeted Java 17 (Spring Boot 3.5 and Spring AI 1.1 both support it) and pointed `JAVA_HOME` at the installed JDK 17.

### 2. App could not connect to the local database
- **Problem:** Startup failed: `FATAL: password authentication failed for user "jobportal"`.
- **Cause:** A Windows PostgreSQL 17 service was already listening on port 5432, so the app connected to that server instead of the Docker container.
- **Fix:** Mapped the Docker Postgres to host port 5433 and changed the default `DB_URL`. Found it by checking which process owned port 5432.

### 3. Planned main source (Adzuna) turned out to be paid
- **Problem:** After sign-up, Adzuna gave only 25 test calls; full access needs £8/month with a card.
- **Cause:** Assumed the API was free without checking its current plans.
- **Fix:** Kept the build at ₹0: switched to official public ATS job-board APIs (Greenhouse, Lever, Ashby, SmartRecruiters) that need no key. Every source sits behind one `JobFetcher` interface, so swapping sources did not touch the rest of the pipeline.

### 4. Free ATS sources have plenty of India jobs but few fresher jobs
- **Problem:** First full run: 1,777 India jobs from 51 company boards, but only ~45 show a clear fresher signal (intern / graduate / entry level / 0–2 years).
- **Cause:** Company career boards mostly post experienced roles; fresher hiring often goes through campus drives and job sites instead.
- **Fix (in progress):** Adding a keyword-search source (Careerjet partner API, India) queried for "fresher", "intern", "graduate". The LLM `is_fresher` tag will give the real count instead of the keyword guess.
