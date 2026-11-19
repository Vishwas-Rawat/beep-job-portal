# What broke and how I fixed it

These are the main problems I ran into while building this, and what I did about each one.

## 1. The AI was calling senior jobs "fresher"

**What happened:** I picked 50 random jobs and labelled them myself. When I compared them with the AI's tags, the fresher tag was right only 84% of the time. Most of the mistakes were clearly senior jobs, like "Senior Full Stack Developer", marked as fresher.

**Why:** In every wrong case, the AI never saw how much experience the job needed. I was cutting each description at 800 characters, and the "3+ years" line was usually after that. One source also had its own "experience level" field that was often wrong. For example, it marked "Senior Full Stack Developer" as "Associate".

**How I fixed it:**
- I added a simple rule in Java: if the job title says "Senior", "Lead", "Intern", "Trainee" and so on, the title wins over the AI. This rule has unit tests.
- I told the AI in the prompt to trust the title more than those fields.
- I now send 2,000 characters instead of 800.

**Result:** fresher accuracy went from 84% to 96%. One change I tried along the way made the role tags worse, so I removed it.

| Version | Role | City | Fresher | All three right |
|---|---|---|---|---|
| v1 | 76% | 98% | 84% | 64% |
| v2 | 70% | 98% | 96% | 62% |
| **v3 (live)** | **76%** | **98%** | **96%** | **70%** |

One honest note: I improved the prompt and tested it on the same 50 jobs, so the real numbers may be a little lower.

## 2. Careerjet kept saying "403" with no reason

**What happened:** Every call to the Careerjet API failed with 403 (forbidden), even though my API key was correct. The error had no message, so I had no idea why.

**Why:** Careerjet had three hidden rules, and I found them one by one:
1. Calls must come from an IP address you register with them.
2. My PC has IPv6, so my requests went out from an address I hadn't registered.
3. They also need a `Referer` header with your website address.

**How I fixed it:** I made Java use IPv4 only. After that, Careerjet finally showed a real error message, which pointed me to the missing `Referer` header. I added that and registered the server's IP.

**Result:** Careerjet now gives about 1,100 fresher and intern jobs per run. It's my biggest source of fresher jobs.

## 3. Testing the live site found real gaps

**What happened:** I tested the live site against an 89-case test sheet and found:
- If a request failed (no internet, or Render's firewall blocking a search like `' OR 1=1 --`), the page kept showing old results with no message.
- The city dropdown had entries that aren't cities: "Unknown", "India", "Karnataka".
- There was no rate limiting and no security headers.

**How I fixed it:**
- The page now shows a clear message like "Could not load jobs. Check your connection."
- The city list only shows real cities.
- Rate limiting: each IP can make 60 requests a minute, and 10 a minute on admin endpoints.
- Security headers, including `Content-Security-Policy`, so other sites can't frame the page and it can't load outside scripts.

**Result:** 77 of the 89 test cases now pass. The rest are known data issues or things that need checking by hand, like other browsers.

## 4. Problems while building "Ask AI" search

Ask AI lets you type something like "data analyst internship in Bangalore". The AI only turns your sentence into filters (role, city, keywords, fresher), and the normal search finds the jobs, so the AI can never make up a job. I tested it with 12 real questions and hit four problems:

- **The app wouldn't start.** My new class had two constructors, and Spring didn't know which one to use. I marked the right one with `@Autowired`. Running it locally first caught this before it went live.
- **The AI searched for the wrong words.** For "I am a 2025 BCA graduate looking for HR jobs", it searched for "2025 bca graduate". I updated the prompt so it ignores the user's own background and only keeps useful search words.
- **"intern" matched "International".** Searching for "intern" also found jobs at "RSM International". I stopped using "intern" as a search word, since the fresher filter already covers internships. That search went from 6 mixed results to 14 correct ones.
- **Some answers took about 6 seconds.** Gemini was sometimes too busy (error 503), and the app waits 5 seconds before trying again. Normally it answers in about 1 second. Now, if the AI takes more than 12 seconds, simple word-matching takes over instead. I also limited AI questions to 6 a minute per person, so the free Gemini quota is never used up before the daily job refresh.
