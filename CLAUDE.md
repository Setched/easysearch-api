# easysearch-api

**Status (2026-09-11):** Spring Boot service comparing product prices across Ozon, Wildberries,
and Yandex Market. Ozon and Wildberries are both real, live-verified integrations now (scrapers
bypassing each site's antibot); Yandex Market is still a stub. The app is deployed and live at
`24easysearch.ru` on a RU VPS.

**When starting a session on this project, lead with this snapshot before diving into any task**,
unless the user's first message already makes their intent clear.

**Recommended next steps:**
1. **Yandex Market real integration** — the only remaining stub; no research done on it yet. Same
   pattern as Ozon/Wildberries: check for a real API first, don't assume a scraper is needed.
2. **Automate deployment** — updates to the live server are still manual (`git pull && docker
   compose build && up -d` over SSH). Worth it once changes start shipping often enough to feel
   the friction.
3. Everything in section 5's "deferred" list is still genuinely deferred, not forgotten.

Java 21 / Spring Boot 4.1, hexagonal architecture, Postgres for search history. Two marketplaces
are backed by sibling Python scraper services, since neither Ozon nor Wildberries has a public
search API.

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
                marketplace/yandexmarket/ YandexMarketMarketplaceClient — hardcoded STUB.
                marketplace/              TimeoutEnforcingMarketplaceClient decorator, applied
                                          uniformly via MarketplaceClientsConfig regardless of
                                          whether a client is real, scraper-backed, or a stub.
                usecase/                 TimeoutEnforcingCompareOffersUseCase — same idea,
                                          bounds the whole comparison.
                persistence/             SearchHistoryRecorderAdapter + JPA + Postgres + Flyway.

ozon-scraper/, wildberries-scraper/   SEPARATE Python/FastAPI services (not part of the Maven
                build), each Playwright-driven. See each one's own README.md for its specific
                antibot technique — they differ (see section 3). Both are the most fragile part
                of the system; either site can change its markup/defenses at any time.
```

Checkstyle (`maven-checkstyle-plugin`, bound to `validate`) enforces Javadoc on every
class/interface/enum/record, English only — `mvn test` fails without it.

## 2. Where things live

- `infrastructure/marketplace/{ozon,wildberries}/` + their scraper directories — the two real
  integrations. `TimeoutEnforcingMarketplaceClient`/`TimeoutEnforcingCompareOffersUseCase` — the
  uniform per-client/overall timeout decorators (necessary because clients vary wildly in speed).
- `architecture/ArchitectureTest.java` + `checkstyle.xml` — layering and Javadoc rules as
  build-breaking checks, not just convention.
- Deployment: root `Dockerfile`, `docker-compose.prod.yml`, `Caddyfile` — see section 4 of the old
  history for how these came together; now just "how the live server runs", not new information.
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
- **Wildberries — real, working**, as of this session. No public API here either, and its antibot
  (WBAAS) turned out to fingerprint the browser itself, not just JS-visible properties — plain
  Playwright, even stealth-patched, was reliably detected. What actually works:
  [`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python) (patches Chromium's
  DevTools-protocol level) driving a real **headful** Chrome via Xvfb, with no manual UA/fingerprint
  faking on top. Full technique and dead-end history in `wildberries-scraper/README.md` — read
  that before touching WB's antibot handling again. Search-timeout/compare-timeout raised to
  45s/50s to fit its 8-20s observed latency.
- Yandex Market is still a hardcoded stub (one canned offer, only for the literal query
  `"iphone 15"`).
- **Deployed and live** at `24easysearch.ru` — RU VPS (Timeweb Cloud), Docker Compose + Caddy
  (auto TLS), SSH key-only/non-root/ufw-hardened. Verified surviving a full reboot
  (`restart: unless-stopped`). One manual `pg_dump` backup exists; no automated backups or CI/CD
  auto-deploy yet (see next-steps snapshot at the top).

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
- Lower priority / explicitly deferred: response caching, outbound rate-limiting/backoff for
  scraper calls, observability/metrics, test coverage reporting, dependency vulnerability scanning
  beyond Dependabot's defaults, automated DB backups, CI/CD auto-deploy.
