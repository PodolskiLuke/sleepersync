# SleeperSync

A dynasty basketball fantasy assistant that syncs directly with the [Sleeper](https://sleeper.com) API. SleeperSync links your Sleeper account, pulls your leagues/rosters/matchups, and layers on top of that a set of tools to help you manage your dynasty team — starting with a live **Draft Helper**.

## Features

- **Account system** — register/login secured with JWT (Spring Security).
- **Sleeper account linking** — connect your Sleeper username, no manual data entry.
- **League selection** — pick which of your Sleeper NBA leagues to work with.
- **Dashboard** — overview of your active league, teams, and season status.
- **Draft Helper** *(live)* — connect to a live Sleeper draft using your username + draft ID to:
  - Track your own picks as they happen
  - See recent picks across the whole draft
  - Browse best-available players overall and by position (PG/SG/SF/PF/C)
- **External Rankings Merge** *(new)* — scrape next-season + dynasty ranking pages and blend them with Sleeper player data to rank remaining draft options (including rookie-only names not yet in local Sleeper sync).
- More tools planned: Start/Sit Advisor, Trade Analyzer, Player Rankings.

## Tech Stack

**Backend**
- Java 17, Spring Boot 3.2.5
- Spring Web, Spring Data JPA, Spring Security (JWT via JJWT)
- PostgreSQL (runtime), H2 (tests)
- Lombok

**Frontend**
- React 18 + Vite
- React Router
- Tailwind CSS
- Axios

## Project Structure

```
pom.xml
src/main/java/com/sleepersync/
  api/            # Sleeper API client + config
  controller/      # REST controllers (auth, leagues, players, drafts)
  model/dto/       # Request/response + Sleeper API DTOs
  model/entity/     # JPA entities (Player, User)
  repository/       # Spring Data repositories
  security/         # JWT filter/util + Spring Security config
  service/          # Business logic

frontend/
  src/
    api/          # Axios client + typed API modules
    components/   # Navbar, ProtectedRoute
    context/       # AuthContext (session/JWT state)
    pages/         # Register, Login, LinkSleeper, SelectLeague, Dashboard, DraftHelper
```

## Getting Started

### Prerequisites

- Java 17+
- Maven (or use the included wrapper, if present)
- Node.js 18+ and npm
- PostgreSQL (or rely on H2 for a quick local run — see note below)

### Backend Setup

1. Configure your database in [src/main/resources/application.properties](src/main/resources/application.properties):

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/sleepersync
   spring.datasource.username=your_db_username
   spring.datasource.password=your_db_password
   ```

2. Set a strong JWT secret (min 32 chars) in the same file:

   ```properties
   jwt.secret=replace-with-a-long-random-string
   ```

3. Run the backend:

   ```powershell
   mvn spring-boot:run
   ```

   The API will be available at `http://localhost:8080`.

4. (Optional) Sync NBA players into the local database once the server is running:

   ```powershell
   curl -X POST http://localhost:8080/api/players/sync
   ```

### Frontend Setup

```powershell
cd frontend
npm install
npm run dev
```

The app runs at `http://localhost:3000` and proxies `/api/*` requests to the Spring Boot backend on port 8080 (see [frontend/vite.config.js](frontend/vite.config.js)).

## Using the Draft Helper

1. Log in and navigate to **Draft Helper** from the dashboard.
2. Enter your **Sleeper username** and the **League Draft ID**.
   - Find the draft ID in the URL of your league's draft page on Sleeper (e.g. `sleeper.com/draft/nba/123456789012345678`).
3. Once connected, the board polls Sleeper every few seconds to show:
   - Your team's picks so far
   - The most recent picks league-wide
   - Best available players overall and filtered by position

## API Overview

| Area    | Base path       |
|---------|-----------------|
| Auth    | `/api/auth`     |
| Leagues | `/api/leagues`  |
| Players | `/api/players`  |
| Drafts  | `/api/drafts`   |
| Rankings| `/api/rankings` |

All routes except `/api/auth/**` require a valid JWT (`Authorization: Bearer <token>`).

### Rankings endpoints

- `GET /api/rankings/scrape`
   - Scrapes configured ranking URLs and returns normalized raw ranking rows.
- `GET /api/rankings/draft/{draftId}/remaining?limit=20`
   - Returns blended rankings for remaining players in that draft.
   - Logic uses: Sleeper synced players + external scraped ranks, minus already drafted IDs.
   - Includes rookie-only names when present in external sources.

### Configure scraping sources

Set these in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
rankings.scraper.enabled=true
rankings.scraper.timeout-ms=8000
rankings.scraper.next-season-url=https://example.com/next-season-rankings
rankings.scraper.dynasty-url=https://example.com/dynasty-rankings
rankings.scraper.rookie-url=https://example.com/rookie-rankings
```

In Draft Helper, use the ranking mode toggle to switch between:
- `Sleeper` (native board + Sleeper stats/ADP)
- `Dynasty + Rookies` (external next-season + dynasty + rookie blended rankings)

## Testing

```powershell
mvn test
```

Tests run against an in-memory H2 database, so no external Postgres instance is required for CI.
