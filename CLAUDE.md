# job-scanner

Finds new jobs in Israel directly from companies' official career pages (Comeet, Greenhouse, Lever, Ashby), discovers companies on its own, and shows them in a LinkedIn-style feed. Java 21 + Spring Boot (batch app, no web server), GitHub Actions, GitHub Pages.

The owner (Guy) is a software engineer who works with Java and Spring Boot, and talks to Claude in Hebrew.

## Source of truth

The design doc and its "work status" tab (decisions with reasons, open points, next step):
https://claude.ai/artifact/B2NQ1F3uBwdaocDGvXYbKw

Read both tabs before changing the design. A design change goes into the doc's design tab, and every decision goes into the "work status" tab, so the two never contradict each other. An idea Guy rejected is recorded in the decisions table as rejected and is not proposed again.

## Rules

- Jobs come only from official career pages. No LinkedIn, no job boards, no third-party aggregators.
- Never bypass blocks: no proxies, no User-Agent or IP rotation. If a site blocks on purpose, ask Guy for approval or drop that source.
- Politeness: one request at a time per ATS, adjustable interval (AIMD), freeze after repeated failures (persisted between runs), visible User-Agent with a contact address, respect robots.txt on HTML pages.
- The tool stays free: GitHub Actions and GitHub Pages in a public repo, no paid services and no language model inside the tool.
- Creating or changing anything visible to others (pushing, publishing, repo settings) needs Guy's explicit approval. As of 2026-11-23 Guy gave a standing approval to proceed through the design stages and tests, and to stop only for something that needs his attention.

## Structure

- `config/filters.yml`: title words, exclusions and location words. Title matching: at least one `field` word and one `role` word (or a `both` word); `seniority` only tags; `exclude` wins.
- `state/`: JSON state, written on the `data` branch (one force-pushed commit per run). Discovery writes the `candidates` branch.
- `site/`: static feed (plain HTML, CSS, JS), reads `feed.json`.
- `.github/workflows/`: `scan.yml` (every 15 minutes by day, hourly by night) and `discover.yml` (weekly).

## Commands

- Build and test: `mvn -B verify` (also runs in GitHub Actions as `build.yml` on every push). Maven Central may be blocked in the Claude cloud sandbox; then rely on the Actions run.
- Scan locally (needs network): `mvn -q package -DskipTests && java -jar target/job-scanner.jar`. State goes to `state/`, the feed to `site/feed.json` (both git-ignored or on the `data` branch).
- Discovery: `java -jar target/job-scanner.jar --scanner.mode=discover` (writes `candidates/candidates.json`, kept on the `candidates` branch).
- Preview the UI with demo data: serve `site/` and open `/?demo`. Live: https://guyor.github.io/job-scanner/
- Seed companies: `config/companies.yml` (ATS plus slug; Comeet slug is `name/uid`).

## Working notes

- Prefer a state-based queue (`next_check_at`) so a skipped cron run only leaves more work for the next one.
- Mark a job closed only after it is missing in two consecutive successful checks.
- A company's first check marks its open jobs as `baseline`, so old jobs never flood the feed as new.
