# easysearch-api

[![CI](https://github.com/Setched/easysearch-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Setched/easysearch-api/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)

Compares product search results across Russian marketplaces (Ozon, Wildberries, Yandex Market)
and returns the cheapest offer, with pagination, sorting, and search history.

## Features

- Search a query across multiple marketplaces in parallel
- Cheapest-offer detection and price-based sorting/pagination
- Per-marketplace and overall timeout budgets, with failure isolation — one marketplace failing
  doesn't fail the whole comparison
- Search history persisted to Postgres
- Hexagonal architecture with enforced boundaries (ArchUnit) and enforced Javadoc (Checkstyle)

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Web, Data JPA, Validation, Actuator) |
| Database | PostgreSQL 16, migrated with Flyway |
| Build | Maven |
| Ozon integration | Sibling Python/FastAPI service (`ozon-scraper/`), Playwright-driven |
| Wildberries integration | Sibling Python/FastAPI service (`wildberries-scraper/`), [`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python)-driven headful Chrome |
| Architecture tests | ArchUnit |
| Style enforcement | Checkstyle (Javadoc required on every type) |

## Architecture

Hexagonal / ports-and-adapters — `domain` has no framework dependencies; `application`
orchestrates; `infrastructure` and `web` are adapters. See [CLAUDE.md](CLAUDE.md) for the full
breakdown, including why each marketplace client is wrapped in a shared timeout decorator and
queried in parallel.

```
domain/               Business model + ports (MarketplaceClient, SearchHistoryRecorder)
application/          Use case orchestration (CompareOffersService)
infrastructure/       Adapters: marketplace clients, Postgres persistence, resilience decorators
web/                  REST controller, DTOs, error handling
ozon-scraper/         Separate Python service — Ozon has no public API, so this scrapes it
wildberries-scraper/  Separate Python service — same idea, for Wildberries
```

## Prerequisites

- JDK 21+
- Docker + Docker Compose (for Postgres, and optionally the Ozon scraper)
- Python 3.11+ (only if running `ozon-scraper` outside Docker)

## Getting started

1. **Clone the repository**
   ```bash
   git clone https://github.com/Setched/easysearch-api.git
   cd easysearch-api
   ```

2. **Start the database**
   ```bash
   docker-compose up -d postgres
   ```

3. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   The API is now available at `http://localhost:8080`. Flyway applies migrations automatically
   on startup.

4. **Verify it's up**
   ```bash
   curl http://localhost:8080/api/ping
   # {"status":"ok"}
   ```

At this point, Yandex Market will respond (with stub data — see
[Known limitations](#known-limitations)), but Ozon and Wildberries results will be empty unless
you also start their scraper services:

5. **(Optional) Start the scrapers** — required for real Ozon/Wildberries results

   Via Docker (simplest):
   ```bash
   docker-compose up -d ozon-scraper wildberries-scraper
   ```

   Or standalone, for local development/debugging (see each service's own README.md for details):
   ```bash
   cd ozon-scraper        # or wildberries-scraper
   pip install -r requirements.txt
   playwright install chromium   # wildberries-scraper: patchright install --with-deps chrome
   uvicorn app.main:app --reload --port 8000   # wildberries-scraper: --port 8001
   ```

   > Don't run a service both standalone and via Docker at once — they'd fight over the same
   > port. See `ozon-scraper/README.md` / `wildberries-scraper/README.md` and
   > [CLAUDE.md](CLAUDE.md) if you hit routing confusion.

## API

### `POST /api/search`

Compares a query across all marketplaces.

**Request body:**

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `query` | string | yes | — | must not be blank |
| `page` | integer | no | `0` | zero-based |
| `size` | integer | no | `20` | 1–100 |
| `sort` | `PRICE_ASC` \| `PRICE_DESC` | no | `PRICE_ASC` | |

**Example:**
```bash
curl -X POST http://localhost:8080/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "iphone 15"}'
```

```json
{
  "query": "iphone 15",
  "bestOffer": { "marketplace": "OZON", "title": "...", "price": 55975.0, "url": "..." },
  "offers": [ { "marketplace": "OZON", "title": "...", "price": 55975.0, "url": "..." } ],
  "totalOffers": 10,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### `GET /api/ping`

Health check. Returns `{"status": "ok"}`.

## Configuration

Set via environment variables (see `src/main/resources/application.yaml` for the full list and
defaults):

| Variable | Purpose | Default |
|---|---|---|
| `OZON_SCRAPER_URL` | Base URL of the `ozon-scraper` service | `http://localhost:8000` |
| `WILDBERRIES_SCRAPER_URL` | Base URL of the `wildberries-scraper` service | `http://localhost:8001` |

Marketplace timeout budgets (`easysearch.marketplaces.search-timeout` / `compare-timeout`, 45s/50s)
are tuned generously to accommodate both Ozon's and Wildberries' antibot-challenge latency — see
the comment in `application.yaml` before lowering them.

## Project structure

```
src/main/java/.../
├── domain/           model/, port/
├── application/      usecase/, service/
├── infrastructure/   marketplace/{ozon,wildberries,yandexmarket}/, usecase/, persistence/
├── web/               controller/, dto/, error/
└── config/
ozon-scraper/          Python FastAPI service for Ozon (see its own README.md)
wildberries-scraper/   Python FastAPI service for Wildberries (see its own README.md)
```

## Testing

```bash
./mvnw test
```

Runs the full suite (unit + `@SpringBootTest`/Testcontainers integration tests, requires Docker),
plus ArchUnit architecture checks and Checkstyle Javadoc enforcement, all as part of the normal
build — no separate commands needed.

## Known limitations

- **Yandex Market is a hardcoded stub** — it only ever returns one canned offer, and only for the
  exact query `"iphone 15"`. Not a real integration yet.
- **Neither Ozon nor Wildberries has a public search API.** Both integrations scrape each site's
  internal API through a browser session that passes that site's antibot challenge — inherently
  fragile, either site can change its markup or defenses at any time. See each service's own
  README.md for what to check if it stops returning results.

## Acknowledgments

The Ozon antibot-bypass technique in `ozon-scraper/` (one persistent browser session, pass the
challenge once, then call Ozon's internal API via `fetch()` from inside the trusted page) is
adapted from [MaxDev43/Marketplace-Parser](https://github.com/MaxDev43/Marketplace-Parser). A
first attempt using [Scrapling](https://github.com/D4Vinci/Scrapling)'s browser automation was
reliably blocked by Ozon's antibot; studying MaxDev43's working implementation is what got the
integration past it.

Wildberries' antibot (WBAAS) needed a different approach — plain Playwright, even stealth-patched,
was reliably fingerprinted. [`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python)
(patches Chromium's DevTools-protocol level, not just JS properties), driving a real headful
Chrome, is what got it past that. See `ozon-scraper/README.md` and `wildberries-scraper/README.md`
for the full story of each.

## Contributing

- `master` is branch-protected — work happens on a `feature/...` branch and lands via PR once CI
  (`mvn test`) passes.
- Every class/interface/enum/record needs a Javadoc comment — enforced by Checkstyle, the build
  fails without it.
- See [CLAUDE.md](CLAUDE.md) for the full set of architectural rules and lessons learned.
