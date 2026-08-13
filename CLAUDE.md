# easysearch-api

A Spring Boot service that compares product search results across Russian marketplaces (Ozon,
Wildberries, Yandex Market) and returns the cheapest offer. Java 21 / Spring Boot 4.1, hexagonal
architecture, Postgres for search history. One marketplace (Ozon) is backed by a sibling Python
scraper service, since Ozon has no public search API.

## 1. Architecture

Hexagonal / ports-and-adapters. Dependency rule enforced by `ArchitectureTest`
(`src/test/java/.../architecture/ArchitectureTest.java`, ArchUnit): domain depends on nothing;
application depends only on domain; infrastructure and web depend on domain + application; nothing
depends on infrastructure or web.

```
web/            HTTP layer — SearchController, DTOs, GlobalExceptionHandler. A "driving adapter":
                translates HTTP <-> domain types, calls into application via CompareOffersUseCase.

application/    usecase/CompareOffersUseCase (port) + service/CompareOffersService (impl).
                Orchestration only, no business rules: queries all MarketplaceClients in
                PARALLEL (via ExecutorService/CompletableFuture), isolates per-client failures
                (searchSafely() catches+logs, never lets one bad marketplace kill the whole
                comparison), then records history.

domain/         model/ — Marketplace, MarketplaceOffer, SearchQuery, ComparisonResult
                  (ComparisonResult.bestOffer() is the one real business rule: cheapest wins).
                port/  — MarketplaceClient (what the domain needs: search a marketplace),
                         SearchHistoryRecorder (what the domain needs: persist a comparison).
                Zero framework/Spring/infrastructure dependencies, by ArchUnit rule.

infrastructure/ Adapters implementing the domain ports, plus cross-cutting decorators:
                marketplace/ozon/        OzonMarketplaceClient — calls the ozon-scraper service
                                          (NOT Ozon directly) over HTTP.
                marketplace/wildberries/ WildberriesMarketplaceClient — hardcoded STUB.
                marketplace/yandexmarket/YandexMarketMarketplaceClient — hardcoded STUB.
                marketplace/             TimeoutEnforcingMarketplaceClient (decorator: wraps
                                          every client with a timeout, applied uniformly via
                                          MarketplaceClientsConfig — raw clients are NOT Spring
                                          components themselves, only the decorated bean is, to
                                          avoid double registration).
                usecase/                 TimeoutEnforcingCompareOffersUseCase (same decorator
                                          pattern, one level up — bounds the whole comparison).
                persistence/             SearchHistoryRecorderAdapter + JPA entity/mapper/repo,
                                          Postgres, Flyway migration.

config/         ClockConfig (injectable Clock), JsonCharsetConfig (forces charset=utf-8 on JSON
                responses — see section 5).

ozon-scraper/   SEPARATE Python/FastAPI service (not part of the Maven build). Playwright-driven.
                Ozon has no public search API, so this scrapes it. See its own README.md for the
                technique (persistent browser session, antibot-challenge-once, then fetch() calls
                inside the trusted page against Ozon's internal page-data API). This is the single
                most fragile part of the system — Ozon can change its markup/defenses at any time.
```

Every marketplace client is wrapped in the SAME timeout decorator and queried in parallel — the
pattern is uniform regardless of whether the client is a real HTTP call, a scraper-backed call, or
a hardcoded stub. Checkstyle (`maven-checkstyle-plugin`, bound to the `validate` phase) enforces
Javadoc on every class/interface/enum/record — `mvn test` fails without it. Javadoc is English
only (explicit user preference — bilingual was tried and reverted).

## 2. Files created this history and why

- `src/main/java/.../infrastructure/marketplace/TimeoutEnforcingMarketplaceClient.java` +
  `MarketplaceClientsConfig.java` + `MarketplaceClientsProperties.java` — per-client timeout,
  applied uniformly, because marketplace clients vary wildly in speed (instant stubs vs. a slow
  scraper) and one slow/hanging marketplace shouldn't hang the whole comparison.
- `src/main/java/.../infrastructure/usecase/TimeoutEnforcingCompareOffersUseCase.java` +
  `CompareOffersUseCaseConfig.java` — same idea, one level up: a hard ceiling on the whole
  comparison even though clients run in parallel now.
- `src/test/java/.../architecture/ArchitectureTest.java` — ArchUnit rules turning the hexagonal
  layering from a convention into a build-breaking rule.
- `checkstyle.xml` + `maven-checkstyle-plugin` in `pom.xml` — same idea for "every class needs
  Javadoc."
- `.github/dependabot.yml` — weekly grouped dependency update PRs (Maven + GitHub Actions).
- `src/main/java/.../config/JsonCharsetConfig.java` — see section 5 (charset gotcha).
- `ozon-scraper/` (whole directory) — the working Ozon integration. See its own README.md.
  `app/ozon_session.py` is the antibot-handling core; `app/ozon.py` is response parsing; `app/main.py`
  is the FastAPI wrapper the Java app calls.
- `CLAUDE.md` (this file) — written because context was running low on a very long session;
  meant to let a fresh session pick up without re-deriving all of the above.

## 3. What's already done

- Hexagonal skeleton, first use case (compare offers), pagination/sorting, persistence — pre-existing
  foundation before the work described here.
- Resilience: per-client + overall timeout decorators, parallel marketplace querying, per-client
  failure isolation.
- Architecture enforcement: ArchUnit layering rules, Checkstyle Javadoc-required rule, Javadoc
  added to every existing class/interface/test.
- Repo hygiene: Dependabot, branch protection on `master` (see section 5 — this is now a hard
  rule, not optional), UTF-8 charset fix for JSON responses.
- **Ozon integration — real, working.** Ozon has no public API. First attempt (Scrapling library,
  fresh browser navigation per request) was reliably blocked by Ozon's antibot and was rolled back
  entirely. Second attempt (current, merged) adapts the technique from
  github.com/MaxDev43/Marketplace-Parser: one persistent browser session, pass the antibot
  challenge once on the homepage with an explicit wait (not network-idle), then call Ozon's
  internal page-data API via `fetch()` executed inside that already-trusted page. Confirmed
  working live (real product names/prices/URLs returned end-to-end through the Java app). Timeouts
  were raised (search-timeout 20s, compare-timeout 25s, Ozon read-timeout 20s) because a cold
  antibot-challenge pass takes 5-10+ seconds.
- Wildberries and Yandex Market are still hardcoded stubs (only ever return one canned offer for
  the literal query `"iphone 15"`) — not real integrations yet.

## 4. Plan / what's next

Roughly in priority order, nothing here is committed to — revisit with the user before starting:

1. **Wildberries real integration** — natural next target. Unverified but plausible signal (from
   MaxDev43/Marketplace-Parser's own README) that Wildberries' antibot is weaker than Ozon's, i.e.
   this might be meaningfully easier than the Ozon saga was. Same pattern to follow: own branch,
   own scraper/adapter if a public API doesn't actually exist (check first, don't assume).
2. **Yandex Market real integration** — same idea, lower priority, no research done on it yet.
3. **Ozon pagination / "load more"** — deliberately not built yet (YAGNI — no UI need for it
   currently). `ozon-scraper/app/ozon.py`'s `search()` docstring documents exactly where to hook
   it in (Ozon's `nextPage` field) when it's actually needed. Would need a matching extension to
   `SearchQuery` (Java) for a page/limit concept, which it doesn't have today.
4. **Deployment** — no Dockerfile for the main Java app yet (only for `ozon-scraper`), no prod
   config (current `application.yaml` has dev-only Postgres credentials hardcoded), no deployment
   target decided. Don't build this until "where do we deploy" is actually answered.
5. Lower priority / explicitly deferred: response caching, outbound rate-limiting/backoff for
   scraper calls, observability/metrics, test coverage reporting, dependency vulnerability
   scanning beyond Dependabot's default alerts.

## 5. Agreements and rules to follow

- **`master` is branch-protected — direct pushes are rejected by GitHub, not just discouraged.**
  Every change goes on its own `feature/xxx` branch with a PR, merged only after the `test` CI
  check passes. This applies to everything now, not just marketplace integrations (that was the
  original, narrower rule — branch protection made it universal in practice).
- **New marketplace integration work gets its own branch**, named `feature/<marketplace>-...`.
  Don't mix two marketplaces' integration work in one branch/PR.
- **Javadoc is required on every class/interface/enum/record**, enforced by Checkstyle
  (`mvn test` fails without it) — English only, not bilingual.
- **Don't claim a marketplace integration "works" without live verification.** Ozon's Seller API
  looked like a real integration for a long time but never actually worked (wrong API entirely).
  Always distinguish "compiles and is mocked in tests" from "verified against the live site."
- **Ozon's antibot is real and can escalate.** Repeated automated attempts from the same
  network/IP can apparently move it from a soft JS-timer challenge to an actual CAPTCHA. Don't
  hammer it with retries while debugging — space out live tests.
- **`docker-compose down` stops every service in `docker-compose.yml`**, not just one — it took
  down Postgres by accident once and caused a confusing, unrelated-looking failure. Use
  `docker-compose stop <service>` or `docker-compose up -d <service>` to target one service.
- **Only one process can bind port 8000 at a time.** `ozon-scraper` can run standalone
  (`uvicorn app.main:app --reload`, binds `127.0.0.1:8000`) or via
  `docker-compose up` (binds via Docker/WSL2, effectively the IPv6 side, `::1`/`::`). Both
  "look like" `localhost:8000` to callers but are different processes with different Chromium
  builds (Windows vs. Linux) — don't run both at once, it causes confusing routing.
- **JSON responses explicitly set `charset=utf-8`** (`JsonCharsetConfig.java` on the Java side,
  explicit `media_type` on the Python side) — Windows PowerShell 5.1's `Invoke-RestMethod` garbles
  Cyrillic text without it. If testing manually in PowerShell: `curl` is aliased to
  `Invoke-WebRequest` with different syntax than real curl — use `Invoke-RestMethod -Uri ... -Method
  Post -ContentType "application/json" -Body '...'`, or `curl.exe` for actual curl syntax.
- **Cloudflare WARP running in the background can silently break outbound network** (e.g. `pip
  install` timeouts to PyPI) even when the user believes it's off — check with `Get-Process -Name
  "*warp*"` / `Get-Service -Name "*warp*"`, not just the tray icon.
- The assistant's own command-execution environment has sometimes had unreliable/sandboxed
  network access (PyPI, raw.githubusercontent.com) independent of the user's own machine, even
  though the filesystem is shared/identical paths — don't assume "I can't reach X" generalizes to
  "the user can't reach X" or vice versa; verify separately.
- Don't build speculative abstractions (filters, pagination, per-marketplace config layers) ahead
  of an actual need — this project's convention is to document the extension point (see section 4,
  item 3) rather than pre-build it.
