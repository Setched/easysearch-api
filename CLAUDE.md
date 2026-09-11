# easysearch-api

**Status (2026-09-12):** Spring Boot service comparing product prices across Ozon, Wildberries,
and Yandex Market. **All three are now real, live-verified integrations** — no stubs left. Ozon
and Wildberries work by bypassing each site's antibot via a scraper; Yandex Market needs no
antibot bypass at all (see section 3). Deployed and live at `24easysearch.ru`, and deployment is
now automated: CI builds and publishes Docker images on every merge to `master`, and a manual
GitHub Actions button (`Deploy` workflow) pulls and restarts them on the server.

**When starting a session on this project, lead with this snapshot before diving into any task**,
unless the user's first message already makes their intent clear.

**Recommended next steps:**
1. **Automated DB backups** — only a single manual `pg_dump` exists. Worth doing now that deploys
   are a one-click action and will likely happen more often — a bad rollout landing without a
   recent backup is the main remaining risk.
2. Everything else in section 4's "deferred" list (response caching, scraper rate-limiting,
   observability/metrics, test coverage reporting, dependency vuln scanning beyond Dependabot) is
   still genuinely deferred — no active need for any of it yet, don't build ahead of one.

Java 21 / Spring Boot 4.1, hexagonal architecture, Postgres for search history. All three
marketplaces are backed by sibling Python scraper services — see section 3 for why each one needs
what it needs.

## 1. Architecture

Hexagonal / ports-and-adapters. Dependency rule enforced by `ArchitectureTest`
(`src/test/java/.../architecture/ArchitectureTest.java`, ArchUnit): domain depends on nothing;
application depends only on domain; infrastructure and web depend on domain + application; nothing
depends on infrastructure or web.

```
web/            HTTP layer — SearchController, DTOs, GlobalExceptionHandler.

application/    CompareOffersUseCase (port) + CompareOffersService (impl). Orchestration only:
                queries all MarketplaceClients in PARALLEL, isolates per-client failures
                (searchSafely()), then records history.

domain/         model/ — Marketplace, MarketplaceOffer, SearchQuery, ComparisonResult
                  (ComparisonResult.bestOffer() is the one real business rule: cheapest wins).
                port/  — MarketplaceClient, SearchHistoryRecorder. Zero framework dependencies.

infrastructure/ marketplace/ozon/         OzonMarketplaceClient — calls the ozon-scraper service.
                marketplace/wildberries/  WildberriesMarketplaceClient — calls wildberries-scraper.
                marketplace/yandexmarket/ YandexMarketMarketplaceClient — calls yandexmarket-scraper.
                marketplace/              TimeoutEnforcingMarketplaceClient decorator, applied
                                          uniformly via MarketplaceClientsConfig to all three
                                          clients regardless of how each one actually works.
                usecase/                 TimeoutEnforcingCompareOffersUseCase — same idea,
                                          bounds the whole comparison.
                persistence/             SearchHistoryRecorderAdapter + JPA + Postgres + Flyway.

ozon-scraper/, wildberries-scraper/,  SEPARATE Python/FastAPI services (not part of the Maven
yandexmarket-scraper/                 build). See each one's own README.md for its technique —
                                       they differ a lot (see section 3). The first two are
                                       Playwright-driven and the most fragile part of the system,
                                       since either site can change its antibot/markup at any
                                       time; yandexmarket-scraper is a plain stateless HTTP client.
```

Checkstyle (`maven-checkstyle-plugin`, bound to `validate`) enforces Javadoc on every
class/interface/enum/record, English only — `mvn test` fails without it.

## 2. Where things live

- `infrastructure/marketplace/{ozon,wildberries,yandexmarket}/` + their scraper directories — all
  three real integrations. `TimeoutEnforcingMarketplaceClient`/`TimeoutEnforcingCompareOffersUseCase`
  — the uniform per-client/overall timeout decorators (necessary because clients vary wildly in
  speed: Yandex Market is a plain HTTP call, Ozon/Wildberries carry antibot-session latency).
- `architecture/ArchitectureTest.java` + `checkstyle.xml` — layering and Javadoc rules as
  build-breaking checks, not just convention.
- Deployment: root `Dockerfile`, `docker-compose.prod.yml`, `Caddyfile` — how the live server runs.
  `.github/workflows/ci.yml`'s `build-and-push` job and `.github/workflows/deploy.yml` — how a
  change gets from merge to running on the server (see section 3's deployment bullet).
- `.github/dependabot.yml` — weekly grouped dependency PRs.

## 3. What's already done

- Hexagonal skeleton, compare-offers use case, pagination/sorting, persistence, resilience
  decorators, ArchUnit/Checkstyle enforcement, Dependabot, branch protection — established
  foundation, not new.
- **Ozon — real, working.** No public API; scrapes Ozon's internal page-data API via one
  persistent browser session that passes the antibot challenge once, then calls the API via
  `fetch()` from inside that trusted page. Technique from
  [MaxDev43/Marketplace-Parser](https://github.com/MaxDev43/Marketplace-Parser); a first attempt
  with a different library was reliably blocked and rolled back. See `ozon-scraper/README.md`.
- **Wildberries — real, working**, as of 2026-09-10. No public API here either, and its antibot
  (WBAAS) turned out to fingerprint the browser itself, not just JS-visible properties — plain
  Playwright, even stealth-patched, was reliably detected. What actually works:
  [`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python) (patches Chromium's
  DevTools-protocol level) driving a real **headful** Chrome via Xvfb, with no manual UA/fingerprint
  faking on top. Full technique and dead-end history in `wildberries-scraper/README.md` — read
  that before touching WB's antibot handling again. Search-timeout/compare-timeout raised to
  45s/50s to fit its 8-20s observed latency.
- **Yandex Market — real, working**, as of 2026-09-11, and unexpectedly simple: unlike Ozon/WB, a
  plain cookie-less `curl` against `market.yandex.ru/search` already returns real product data — no
  antibot challenge at all. The page embeds it as schema.org JSON-LD (`<script
  type="application/ld+json">`, an `ItemList` of `Product`/`Offer`), the same structured data sites
  serve to search-engine crawlers. `yandexmarket-scraper` is therefore just a stateless `httpx` GET
  + JSON-LD parse, no Playwright/session/antibot handling needed. Kept as a separate Python service
  (not embedded directly in the Java app) for architectural consistency with Ozon/WB, by explicit
  user choice. See `yandexmarket-scraper/README.md`.
- **Deployed and live** at `24easysearch.ru` — RU VPS (Timeweb Cloud), Docker Compose + Caddy
  (auto TLS), SSH key-only/non-root/ufw-hardened. Verified surviving a full reboot
  (`restart: unless-stopped`).
- **Deployment is automated**, as of 2026-09-11: `ci.yml`'s `build-and-push` job builds all 4
  service images and publishes them to `ghcr.io/setched/easysearch-api/*` (tagged `:latest` and by
  commit SHA) on every push to `master`. `docker-compose.prod.yml` references those images instead
  of building locally. Actually rolling out to the server is still a deliberate, manual action —
  `deploy.yml`, triggered via `workflow_dispatch` in the GitHub Actions UI (not automatic on merge)
  — because there are still no automated DB backups. It SSHes in, runs `git pull --ff-only` (to
  sync `docker-compose.prod.yml`/`Caddyfile` themselves — `docker compose pull` alone can't detect
  that the compose file changed, learned the hard way on the first real run), then `docker compose
  pull && up -d`. Rollback = re-run with an older commit SHA as the `image_tag` input.
  One manual `pg_dump` backup exists; no automated ones yet (see next-steps snapshot at the top).

## 4. Agreements and rules to follow

- **`master` is branch-protected.** Every change goes on its own `feature/xxx` branch with a PR,
  merged only after the `test` CI check passes. Applies to everything, not just marketplace work.
- **New marketplace integration work gets its own branch**, `feature/<marketplace>-...`. Don't mix
  two marketplaces' work in one branch/PR.
- **Javadoc required on every class/interface/enum/record** (Checkstyle, English only).
- **Don't claim a marketplace integration "works" without live verification.** Ozon's Seller API
  looked real for a long time but never worked; Wildberries needed 4 attempts before one actually
  passed its antibot live. "Compiles and is mocked in tests" ≠ "verified against the live site."
- **Antibots here are real and can escalate** — Ozon's and Wildberries' both. Don't hammer either
  with repeated live attempts while debugging; space them out.
- **Cloudflare WARP re-enables itself between machine restarts** and silently breaks scraper
  requests (looks like a hard antibot block, not a network issue) — check
  `Get-Process -Name "*warp*"` before live-testing either scraper, every time, not just once.
- **`docker-compose down` stops every service**, not just one (took down Postgres by accident
  once). Use `docker-compose stop <service>` to target one.
- Postgres only applies `POSTGRES_PASSWORD` on a volume's first init — changing the env var later
  does nothing until the volume is recreated. Bit twice this project already (dev and prod).
- **JSON responses explicitly set `charset=utf-8`** — Windows PowerShell 5.1's `Invoke-RestMethod`
  garbles Cyrillic without it. For manual testing in PowerShell, use `curl.exe` (real curl syntax)
  or `Invoke-RestMethod` with its own syntax — plain `curl` there is aliased to
  `Invoke-WebRequest`.
- Don't build speculative abstractions ahead of an actual need (e.g. Ozon pagination — documented
  as an extension point in `ozon-scraper/app/ozon.py`'s docstring, not pre-built).
- **`deploy.yml` must `git pull` before `docker compose pull`** — the server's compose file
  doesn't update itself, and `docker compose pull` silently no-ops for any service whose local
  compose file still says `build:` instead of `image:`, instead of erroring. Bit once already:
  the first real deploy run reported success but changed nothing on the server.
- Lower priority / explicitly deferred: response caching, outbound rate-limiting/backoff for
  scraper calls, observability/metrics, test coverage reporting, dependency vulnerability scanning
  beyond Dependabot's defaults, automated DB backups.
