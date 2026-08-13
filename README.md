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
| Architecture tests | ArchUnit |
| Style enforcement | Checkstyle (Javadoc required on every type) |

## Architecture

Hexagonal / ports-and-adapters — `domain` has no framework dependencies; `application`
orchestrates; `infrastructure` and `web` are adapters. See [CLAUDE.md](CLAUDE.md) for the full
breakdown, including why each marketplace client is wrapped in a shared timeout decorator and
queried in parallel.

```
domain/          Business model + ports (MarketplaceClient, SearchHistoryRecorder)
application/     Use case orchestration (CompareOffersService)
infrastructure/  Adapters: marketplace clients, Postgres persistence, resilience decorators
web/             REST controller, DTOs, error handling
ozon-scraper/    Separate Python service — Ozon has no public API, so this scrapes it
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

At this point, Wildberries and Yandex Market will respond (with stub data — see
[Known limitations](#known-limitations)), but Ozon results will be empty unless you also start
the scraper service:

5. **(Optional) Start the Ozon scraper** — required for real Ozon results

   Via Docker (simplest):
   ```bash
   docker-compose up -d ozon-scraper
   ```

   Or standalone, for local development/debugging (see `ozon-scraper/README.md` for details):
   ```bash
   cd ozon-scraper
   pip install -r requirements.txt
   playwright install chromium
   uvicorn app.main:app --reload
   ```

   > Don't run both at once — they'd both try to bind port 8000. See `ozon-scraper/README.md` and
   > [CLAUDE.md](CLAUDE.md) if you hit routing confusion between the two.

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

Marketplace timeout budgets (`easysearch.marketplaces.search-timeout` / `compare-timeout`) are
tuned generously to accommodate Ozon's antibot-challenge latency — see the comment in
`application.yaml` before lowering them.

## Project structure

```
src/main/java/.../
├── domain/           model/, port/
├── application/      usecase/, service/
├── infrastructure/   marketplace/{ozon,wildberries,yandexmarket}/, usecase/, persistence/
├── web/               controller/, dto/, error/
└── config/
ozon-scraper/          Python FastAPI service for Ozon (see its own README.md)
```

## Testing

```bash
./mvnw test
```

Runs the full suite (unit + `@SpringBootTest`/Testcontainers integration tests, requires Docker),
plus ArchUnit architecture checks and Checkstyle Javadoc enforcement, all as part of the normal
build — no separate commands needed.

## Known limitations

- **Wildberries and Yandex Market are hardcoded stubs** — they only ever return one canned offer,
  and only for the exact query `"iphone 15"`. Not real integrations yet.
- **Ozon has no public search API.** The integration scrapes Ozon's internal page-data API through
  a Playwright-driven browser session that passes Ozon's antibot challenge. This is inherently
  fragile — Ozon can change its markup or defenses at any time. See `ozon-scraper/README.md` for
  what to check if it stops returning results.
- No production deployment configuration yet (dev-only datasource credentials in
  `application.yaml`, no Dockerfile for the main app).

## Contributing

- `master` is branch-protected — work happens on a `feature/...` branch and lands via PR once CI
  (`mvn test`) passes.
- Every class/interface/enum/record needs a Javadoc comment — enforced by Checkstyle, the build
  fails without it.
- See [CLAUDE.md](CLAUDE.md) for the full set of architectural rules and lessons learned.
