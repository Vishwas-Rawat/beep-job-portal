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

### 5. Careerjet would not accept a GitHub profile as the "website"
- **Problem:** Careerjet's publisher sign-up rejected `github.com/<user>` as the website address.
- **Cause:** It wants the real site that will show the jobs.
- **Fix:** Deployed to Render a day earlier than planned, so the live app URL can be used for the sign-up.

### 6. First Render deploys failed on the database connection
- **Problem:** Deploy 1: `UnknownHostException: HOST`. Deploys 2–4: `28P01 password authentication failed for user 'neondb_owner'`.
- **Cause:** (a) A placeholder from the setup notes was pasted as the real host. (b) The DB password was typed by hand, but it must be the one Neon generated for the role.
- **Fix:** Rebuilt `DB_URL`, `DB_USER`, `DB_PASSWORD` from one Neon connection string (same project, Singapore region). The error messages in the logs pointed straight at each cause.

### 7. One deploy hung for 15 minutes with no logs
- **Problem:** Startup stopped logging and Render waited on the health check until it gave up.
- **Cause:** The Postgres JDBC driver has no socket timeout by default, so a stalled connection waits forever instead of failing.
- **Fix:** Added `connectTimeout`, `loginTimeout`, `socketTimeout` and a Hikari `connection-timeout`. Next deploy failed in under 2 minutes with a clear error, which made #6 easy to fix.

### 9. Careerjet API answered 403 with an empty body
- **Problem:** Every Careerjet call failed with HTTP 403 and no message, even with a valid key.
- **Cause:** Three separate rules, found one at a time: (a) calls must come from an IP declared in the partner dashboard; (b) the PC has IPv6, so requests left from an undeclared IPv6 address even after the home IPv4 was declared; (c) once IPv4 was forced, the API finally said `Undeclared referrer. Please add a Referer header.`
- **Fix:** Force IPv4 in the JVM (`java.net.preferIPv4Stack=true`), send `Referer: https://beep-jobs.onrender.com/`, declare the server IP. Also found the real page-size parameter is `pagesize` (max ~99), not `page_size` as documented. Result: 1,122 fresher/intern posts per run, 706 new after dedupe.
- **Render's IP:** Render lists two /24 outbound ranges (512 IPs), but Careerjet allows only 8 single IPs. Added a token-protected `/api/admin/egress-ip`; 3 calls returned the same IP, so that one IP was declared. Risk: it may change after a redeploy, so re-check if Careerjet drops to 0.
- **Then 401 "Invalid API key provided" on Render only:** the key pasted into Render's env was wrong while the local `.env` was right. The 403 → 401 change showed the IP was now accepted, so the key was the only thing left. Re-pasted it; live run then fetched 1,122 Careerjet posts (705 new, 2,358 total).

### 10. Planned LLM model was closed to new users
- **Problem:** First Gemini call returned 404: `gemini-2.5-flash is no longer available to new users`.
- **Cause:** Model lineup changed; older plans/tutorials still name 2.5.
- **Fix:** Listed models with the key, tested a few, chose `gemini-3.5-flash-lite` (cheapest current one). Model is a config value, so nothing in code changed.

### 11. Spring AI called the wrong URL for Gemini
- **Problem:** Gemini's OpenAI-compatible endpoint is `.../v1beta/openai/chat/completions`, but Spring AI appends `/v1/chat/completions` to the base URL.
- **Fix:** Set `spring.ai.openai.chat.completions-path=/chat/completions` (env `LLM_COMPLETIONS_PATH`).

### 12. Gemini returned 503 (overloaded) during the full tagging run
- **Problem:** Several `HTTP 503` errors while tagging 2,315 posts.
- **Cause:** Provider-side overload; free tier also has unpublished rate limits.
- **Fix:** Spring AI retry with exponential backoff on 429/503 (5 attempts, 5s → 60s). All retries succeeded: 0 failed batches, 0 fallbacks, 1 invalid role out of 2,315.

### 8. Neon project first created in the wrong region
- **Problem:** Neon defaulted to AWS US East (Ohio) while Render runs in Singapore.
- **Cause:** Didn't pick the region on project creation.
- **Fix:** Recreated the empty project in Singapore before any data went in. Avoids ~200 ms extra per query (the first refresh does ~1,700 inserts).
