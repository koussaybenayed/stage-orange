# Production Deployment Documentation — Device Monitoring System

**Project:** Device Monitoring System (Orange DB monitoring)
**Git repository:** https://github.com/koussaybenayed/stage-orange (branch `main`)
**Last verified against source:** WAMP (local dev environment), August 2026

This document is intended for the **Infrastructure team** to deploy and host the application in production. It covers the database (`orange_db`), the backend (Spring Boot REST API), the frontend (Angular), and the deployment steps.

---

## 1. System Overview

| Component | What it does |
|---|---|
| **Frontend** | Angular 17 single-page app. Dashboard, device list/search, device detail, incidents view, and a Leaflet problem map. Served as static files behind Nginx. |
| **Backend** | Spring Boot 3.2 REST API. Reads device metrics (`acsmaxbox_5g`), incident records (`re_u_n2_29_06`), radio cell data, and site coordinates from MySQL `orange_db`. Port **8081**. |
| **Database** | MySQL 8.0+ database named `orange_db` on WAMP (local) — must be recreated in production with the same schema/data. |

> Note: the application is **read-heavy and data-heavy**. The largest table (`acsmaxbox_5g`) currently holds ~29.3M rows (~7.3 GB of data + ~1 GB of indexes). Plan disk and memory accordingly.

---

## 2. Architecture Diagram

```
Browser (users)
      │  HTTP/HTTPS :80
      ▼
┌──────────────────────────┐
│  Frontend (Angular SPA)  │  nginx:1.25-alpine — serves /dist
│  served by Nginx         │  /api/* proxied to backend
└───────────┬──────────────┘
            │  /api/*  →  proxy_pass http://backend-service:8081/
            ▼
┌──────────────────────────┐
│  Backend (Spring Boot)   │  eclipse-temurin:17 JRE, port 8081
│  Java 17 / Spring Boot   │  JPA/Hibernate, JdbcTemplate
└───────────┬──────────────┘
            │  JDBC (jdbc:mysql://.../orange_db)
            ▼
┌──────────────────────────┐
│  MySQL 8.0+  orange_db   │  InnoDB + MyISAM tables
└──────────────────────────┘
```

Request flow: Nginx → `/api/...` proxied to backend → JDBC → MySQL.

---

## 3. Technology Stack (exact versions)

### Backend — `backend/pom.xml`
- Java **17**
- Spring Boot **3.2.0** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`)
- MySQL Connector/J **8.0.33**
- Hibernate (via Spring Data JPA) with `MySQL8Dialect`
- Lombok, Spring Boot DevTools (runtime only)
- Build: Maven → artifact `device-monitoring-backend-1.0.0.jar`

### Frontend — `frontend/package.json`
- Angular **17.x** (CLI 17.3.17), TypeScript **5.2**
- Chart.js **4.4** + ng2-charts **4.1**
- Leaflet **1.9.4** (map), RxJS **7.8**
- Build: `ng build --configuration production` → `dist/device-monitoring-frontend/`
- Dev proxy: `frontend/proxy.conf.json` forwards `/api` → `http://localhost:8081`

### Runtime containers (already in the repo)
- Backend: `backend/Dockerfile` → `eclipse-temurin:17-jre`, `EXPOSE 8081`
- Frontend: `frontend/Dockerfile` → `nginx:1.25-alpine`, `EXPOSE 80`
- Frontend Nginx config: `frontend/nginx.conf` (proxies `/api/` to `http://backend-service:8081/`)

---

## 4. Database — `orange_db`

### 4.1 Connection settings (as configured in the backend)
Defined in `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/orange_db
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=none          # schema is managed externally, NOT auto-created
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
```

Production must provide a real user/password (see section 6). `ddl-auto=none` means the schema must exist; the application never creates or alters tables.

### 4.2 Tables (measured on the source WAMP database)

| Table | Engine | Rows (approx) | Data size | Purpose |
|---|---|---|---|---|
| `acsmaxbox_5g` | InnoDB | 29,279,965 | ~7.3 GB | Device KPIs (main monitoring data) |
| `acsmaxbox_5g_new` | InnoDB | 6,513,413 | ~1.6 GB | Staging/new device load (NOT used by app) |
| `acsmaxbox_latest_incidents` | MyISAM | 1,359 | ~300 KB | Latest device snapshot per incident |
| `etat_c_band` | MyISAM | 210 | ~16 KB | C-band congestion state → action mapping |
| `fixbox_combined_table` | MyISAM | 625,039 | ~10 MB | MSISDN ↔ IMSI lookup |
| `hz` | MyISAM | 1,269,832 | ~128 MB | Network handset status/error events |
| `lte_cell_info_lm_2026_06_30_11_32_27_244` | MyISAM | 29,283 | ~13 MB | LTE cell reference data (huge flat file import) |
| `nr_cells` | MyISAM | 4,087 | ~748 KB | 5G NR cell reference data |
| `re_u_n2_29_06` | MyISAM | 2,704 | ~1.5 MB | Customer incident tickets ("Réunion" export) |
| `site_otn` | MyISAM | 2,530 | ~81 KB | Site names → GPS coordinates |

**Important sizing note:** `acsmaxbox_5g` dominates. The whole DB is roughly **9–10 GB** including indexes. In production, provision at least **20 GB** of storage (plus growth), and consider converting the large MyISAM tables to InnoDB for crash safety (see section 8.4).

### 4.3 Schema details

#### `acsmaxbox_5g` — device KPIs (main table, 29M rows)
All signal columns are stored as `TEXT` (no numeric typing) — the app trims/parses them in Java.

| Column | Type | Notes |
|---|---|---|
| `SN` | text | **Logical primary key** (serial number) |
| `IMSI` | text | Contains trailing CR `\r`; app cleans with `.replace("\r","").trim()` |
| `id` | text | Another identifier (unused by app) |
| `deviceId` | text | Used in search |
| `productclass` | text | |
| `cellid` | text | Format `"eNodeBId-LocalCellId"` (e.g. `1234-5`), parsed by app |
| `pci4g`, `pci5g` | text | Physical Cell IDs |
| `rscp4g`, `rscp5g`, `rssi4g`, `rssi5g` | text | RSCP/RSSI metrics |
| `rsrp4g`, `rsrp5g` | text | RSRP metrics (4G / 5G) |
| `sinr4g`, `sinr5g` | text | SINR metrics |
| `rsrq4g`, `rsrq5g` | text | RSRQ metrics |
| `uplink_max_thrp`, `downlink_max_thrp` | text | Max throughput |
| `signalquality`, `linkquality` | text | |
| `date`, `hour`, `timestamp` | text | Measurement time (used for default sort `timestamp desc`) |
| `type` | text | |

> The app queries this table with `LIMIT`/pagination. No primary-key constraint exists in MySQL; the entity treats `SN` as the `@Id`.

#### `re_u_n2_29_06` — incident tickets (2,704 rows)
Columns: `Numéro_de_la_demande` (text), `Créé_le` (datetime), `Sujet` (text), `Description` (text), `MSISDN_concerné` (text), `Offre__Contrat` (text).

- The app filters incidents where `Sujet` contains "Déconnexion", "Echec de connexion", or "Lenteur" **AND** `Offre__Contrat` contains "MAXBOX 5G".
- In the entity `ReUn22906`, `MSISDN_concerné` is mapped to a `Long` (`msisdn`) even though the column is `TEXT` in MySQL — production data must contain numeric strings only, otherwise JPA conversion fails.

#### `lte_cell_info_lm_2026_06_30_11_32_27_244` — LTE cell reference (29,283 rows)
Huge imported sheet (60+ columns: `eNodeB_Id`, `Local_cell_identity`, `Cell_Name`, `Cell_ID`, `Physical_cell_ID`, `Frequency_band`, …). The app uses `eNodeB_Id` + `Local_cell_identity` to resolve a `Cell_Name` for a device's `cellid`. The entity exposes only a subset (`Unnamed__62`, `enodeBID`, `Local_cell_identity`, `Cell_Name`).

#### `nr_cells` — 5G NR cells (4,087 rows)
Columns: `Subarea`, `RAT`, `Operator`, `gNodeB_ID`, `NR_NE_Name`, `Cell_Name`, `Cell_ID`, `TAC`, `Frequency_Band`, `Physical_Cell_ID`, `clé`, … The app maps `clé` (key = `prefix + PCI5G`) → `Cell_Name` to find the 5G cell name for a device.

#### `site_otn` — site coordinates (2,530 rows)
Columns: `site`, `Longitude_Sector` (double), `Latitude_Sector` (double), `CoverageType`. App caches `site → [lat, lng]` in memory at startup/first use.

#### `etat_c_band` — congestion/action lookup (210 rows)
Columns: `Sites`, `sect`, `Étiquettes_de_lignes` (text), `Moyenne_de_NR_DL_User_Throughput_(Mbps)` (double), `Action` (text). App loads `Étiquettes_de_lignes → Action` to mark a 5G cell as "congestionnée".

#### `hz` — network status events (1.27M rows)
Columns: `Time` (text), `MSISDN` (bigint), `IMSI`, `IMSI_2`, `Site_ID`, `site_Name`, `error_code`, `status`, `code_resv1`, `code_resv2`, `APN`. 
**Mapping rule used by the app:** `hz.MSISDN = original MSISDN + 21600000000` (`HZ_MSISDN_OFFSET`). The app matches incidents to `hz` rows within a ±3 day window (`HZ_WINDOW_DAYS`).

#### `fixbox_combined_table` — MSISDN ↔ IMSI (625K rows)
Columns: `MSISDN` (bigint), `IMSI` (bigint). Referenced by a repository but **currently unused by service logic**.

#### `acsmaxbox_5g_new` / `acsmaxbox_latest_incidents`
- `acsmaxbox_5g_new`: identical schema to `acsmaxbox_5g`; not used by the application.
- `acsmaxbox_latest_incidents`: same columns as `acsmaxbox_5g`; used by native query `findAllByImsiIn` to map incident MSISDNs to devices.

### 4.4 How MSISDN → device lookup works (business rule)
1. Incident gives an MSISDN (e.g. `4123456789`).
2. Backend derives the device IMSI: `"60501" + zero-padded 10-digit MSISDN` → e.g. `6050104123456789`.
3. `IMSI` in `acsmaxbox_5g` / `acsmaxbox_latest_incidents` (after stripping `\r`) is matched against that value.
4. Device's `cellid` (`eNodeBId-LocalCellId`) is resolved through `lte_cell_info...` to a cell name, then to a site prefix (first 8 chars) → coordinates from `site_otn`, and 5G cell via `nr_cells` (`prefix + PCI5G`).

---

## 5. Backend — Spring Boot API

### 5.1 Build & run (dev)
```bash
cd backend
mvn clean package -DskipTests
java -jar target/device-monitoring-backend-1.0.0.jar
# listens on http://localhost:8081
```

### 5.2 Runtime requirements
- Java 17+ runtime (JRE)
- MySQL 8.0+ reachable at the JDBC URL in `application.properties`
- No external secrets/config server; credentials are in `application.properties`

### 5.3 API Endpoints

#### Device API — `/api/devices` (table `acsmaxbox_5g`)
| Method | Path | Description | Query params |
|---|---|---|---|
| GET | `/api/devices` | Paginated device list | `page` (0), `size` (10), `sort` (`timestamp,desc`) |
| GET | `/api/devices/{id}` | Device by serial number (SN) | — |
| GET | `/api/devices/search` | Search by SN / deviceId / cellid (LIKE) | `searchTerm`, `page`, `size` |
| POST | `/api/devices` | Create device | JSON body |
| PUT | `/api/devices/{id}` | Update device (SN, SINR, RSRP, RSRQ) | JSON body |
| DELETE | `/api/devices/{id}` | Delete device | — |
| GET | `/api/devices/stats/total` | Total device count | — |
| GET | `/api/devices/all` | First 2000 rows (`findLatest(2000)`) | — |
| GET | `/api/devices/by-msisdn/{msisdn}` | Devices for an MSISDN (with cell/geo info) | — |

#### Incident API — `/api/incidents` (table `re_u_n2_29_06`)
| Method | Path | Description |
|---|---|---|
| GET | `/api/incidents` | Filtered incidents (Déconnexion / Echec de connexion / Lenteur + MAXBOX 5G) |
| GET | `/api/incidents/with-device-info` | Incidents enriched with device KPIs, cell names, coordinates, HZ errors |
| GET | `/api/incidents/stats/overview` | `{ totalIncidents, lastDay, last7Days }` |
| GET | `/api/incidents/stats/by-type` | Count by `Sujet` |
| GET | `/api/incidents/stats/by-offre` | Count by `Offre__Contrat` |
| GET | `/api/incidents/stats/by-date` | Count by date |
| GET | `/api/incidents/stats/hzerror` | HZ error distribution per incident (within ±3 days) |
| GET | `/api/incidents/top-zones` | Top congested zones by site prefix | `limit` (10) |

### 5.4 Important backend behaviors the infra team should know
- **CORS is wide open** (`allowedOrigins("*")` in `DeviceMonitoringApplication.java`) — fine for a stage app; restrict in production.
- Several heavy endpoints (`/with-device-info`, `/top-zones`, `/stats/hzerror`) compute in Java memory and fire **multiple SQL queries per incident** (`hz`, `acsmaxbox_latest_incidents`, reference caches). They are not cheap — expect higher CPU/latency.
- Reference data (`site_otn`, `lte_cell_info…`, `nr_cells`, `etat_c_band`) is cached **in JVM memory** on first use (not refreshed). Restart the backend after updating these tables.
- The `hz` window uses string timestamps (`yyyy-MM-dd HH:mm:ss`); keep that format in prod data.
- `searchDevices` / default sort reference `timestamp`, so index/sort by `timestamp` in MySQL for acceptable performance on 29M rows.

---

## 6. Frontend — Angular SPA

### 6.1 Build & run (dev)
```bash
cd frontend
npm install
npm start          # ng serve --proxy-config proxy.conf.json  → http://localhost:4200
```
The dev proxy (`proxy.conf.json`) sends `/api` → `http://localhost:8081`.

### 6.2 Production build
```bash
cd frontend
npm run build:prod          # = ng build --configuration production
# output: dist/device-monitoring-frontend/ (static files)
```
- `src/environments/environment.prod.ts` → `apiUrl: '/api'` (same-origin relative URL).
- The SPA is designed to sit **behind Nginx on the same origin**, with Nginx proxying `/api/` to the backend. There is **no hardcoded backend host** in the production bundle.

### 6.3 Nginx config (already provided — `frontend/nginx.conf`)
```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {                      # SPA history-mode fallback
        try_files $uri $uri/ /index.html;
    }

    location /api/ {                  # reverse proxy to backend
        proxy_pass http://backend-service:8081/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
> **Action for infra:** change `proxy_pass` to the production backend host/container DNS, enable TLS (443), and set `server_name` to the real domain.

---

## 7. Deployment Options

### Option A — Docker Compose (recommended)
Dockerfiles already exist for both apps. Create a `docker-compose.yml` at repo root:

```yaml
services:
  db:
    image: mysql:8.0
    container_name: orange_db
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: orange_db
      MYSQL_USER: orange_app
      MYSQL_PASSWORD: CHANGE_ME_STRONG
      MYSQL_ROOT_PASSWORD: CHANGE_ME_ROOT
    volumes:
      - mysql-data:/var/lib/mysql
      - ./orange_db_dump.sql:/docker-entrypoint-initdb.d/01-dump.sql:ro
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-proot"]
      interval: 10s
      timeout: 5s
      retries: 10

  backend:
    build: ./backend
    container_name: orange-backend
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/orange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: orange_app
      SPRING_DATASOURCE_PASSWORD: CHANGE_ME_STRONG
      SERVER_PORT: 8081
    ports:
      - "8081:8081"

  frontend:
    build: ./frontend
    container_name: orange-frontend
    restart: unless-stopped
    depends_on:
      - backend
    ports:
      - "80:80"

volumes:
  mysql-data:
```

> Note: in the frontend Nginx config, `backend-service` is the DNS name for the backend container. In docker-compose the backend service name must be `backend-service` (or edit `frontend/nginx.conf` accordingly).

### Option B — Bare metal / VM
1. **Database:** install MySQL 8.0+, import the dump (section 7.1), create app user.
2. **Backend:** install Java 17, copy `device-monitoring-backend-1.0.0.jar`, run with env overrides:
   ```bash
   SPRING_DATASOURCE_URL=jdbc:mysql://dbhost:3306/orange_db \
   SPRING_DATASOURCE_USERNAME=orange_app \
   SPRING_DATASOURCE_PASSWORD=secret \
   java -jar device-monitoring-backend-1.0.0.jar
   ```
   (Run as a systemd service.)
3. **Frontend:** `npm run build:prod`, serve `dist/device-monitoring-frontend/` with Nginx using the provided config.

### 7.1 Moving the database
Create a dump on WAMP and import it in production:
```powershell
# Export (WAMP)
& "C:\wamp64\bin\mysql\mysql8.3.0\bin\mysqldump.exe" -u root --single-transaction --routines --triggers orange_db > orange_db_dump.sql
```
```bash
# Import (production)
mysql -u orange_app -p orange_db < orange_db_dump.sql
```

> ⚠️ The 29M-row `acsmaxbox_5g` will make the dump large (several GB) and slow. Plan the migration window and use `--single-transaction` for a consistent snapshot. Consider `--quick` and gzip (`| gzip`) to speed transfer.

---

## 8. Production Checklist & Configuration Changes

### 8.1 Required before going live
- [ ] Set real DB credentials (env vars or edit `application.properties`). Current file has **empty root password** — do not ship that.
- [ ] Set the DB host: `localhost:3306` → production host/container DNS.
- [ ] Set `server_name` and TLS on Nginx; change the `proxy_pass` target.
- [ ] Restrict CORS in `DeviceMonitoringApplication.java` (`allowedOrigins("*")`) to the real domain.
- [ ] Reduce verbose logging: `logging.level.com.orange.monitoring=DEBUG` → `INFO`, `org.hibernate.SQL=DEBUG` → off (perf + log volume).
- [ ] Add a proper `spring.jpa.properties.hibernate.jdbc.batch_size` / query tuning if needed for the heavy read endpoints.
- [ ] Run backend behind systemd / supervisor / container restart policy.

### 8.2 Environment variables (Spring relaxed binding — overrides `application.properties`)
| Variable | Default (dev) | Production value |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/orange_db` | prod DB URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | dedicated app user |
| `SPRING_DATASOURCE_PASSWORD` | *(empty)* | strong password |
| `SERVER_PORT` | `8081` | `8081` (or 80 behind LB) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `none` | `none` (must stay `none`) |

### 8.3 Security notes
- Application has **no authentication/authorization** — anyone who can reach it can read/edit all devices. Put it behind a VPN, firewall, or add auth before exposing publicly.
- Data contains subscriber identifiers (IMSI, MSISDN). Treat the DB and API as sensitive (PII) — encrypt at rest, restrict network access, use TLS in transit.
- Keep `.env`/credentials out of the repository.

### 8.4 Database operations notes for infra
- `acsmaxbox_5g` has **no index on `timestamp`** — the default sort (`timestamp,desc`) will cause slow full scans on 29M rows. Create an index:
  ```sql
  ALTER TABLE acsmaxbox_5g ADD INDEX idx_timestamp (`timestamp`);
  ALTER TABLE acsmaxbox_5g ADD INDEX idx_imsi (`IMSI`(20));
  ALTER TABLE acsmaxbox_latest_incidents ADD INDEX idx_imsi (`IMSI`(20));
  ```
- Consider migrating MyISAM tables (`hz`, `fixbox_combined_table`, `re_u_n2_29_06`, reference tables) to **InnoDB** for crash safety and row-level locking:
  ```sql
  ALTER TABLE hz ENGINE=InnoDB; ALTER TABLE fixbox_combined_table ENGINE=InnoDB;
  ALTER TABLE re_u_n2_29_06 ENGINE=InnoDB; /* and the reference tables */
  ```
  Test performance impact first (MyISAM was likely chosen for import speed).
- `acsmaxbox_5g_new` is a duplicate staging table (~1.6 GB) not used by the app — exclude it from the prod migration if not needed.
- Table names are case-sensitive on Linux (e.g. `re_u_n2_29_06`, `acsmaxbox_5g`). Match exactly.

---

## 9. Operational / Troubleshooting Notes

| Symptom | Likely cause / fix |
|---|---|
| Backend fails to start | DB not reachable, wrong credentials, or `orange_db` not imported. Check `SPRING_DATASOURCE_*`. |
| 404 on `/api/...` from Nginx | `proxy_pass` target wrong, or backend not listening on 8081. |
| Slow device list | Missing `timestamp` index on `acsmaxbox_5g` (29M rows). |
| Blank page in production | Nginx `try_files` missing (must fall back to `/index.html` for SPA routing). |
| Incidents show no device info | MSISDN → IMSI mapping (`60501%010d`) produces no match in `acsmaxbox_latest_incidents`; check IMSI data / CR chars. |
| HZ errors empty | `hz` data not imported, timestamps not in `yyyy-MM-dd HH:mm:ss`, or MSISDN offset (21600000000) mismatch. |
| Map shows no markers | `site_otn` empty or `site` prefix (first 8 chars of cell name) not present in `site_otn`. |
| Stale cell/site info | Reference caches load once per JVM; restart backend after updating `lte_cell_info…`, `nr_cells`, `site_otn`, `etat_c_band`. |

---

## 10. Files Inventory (what to hand to infra)

```
stage-orange/
├── backend/
│   ├── Dockerfile                        # multi-stage build → temurin 17 JRE, port 8081
│   ├── pom.xml                           # Spring Boot 3.2.0, Java 17, MySQL 8
│   └── src/main/resources/application.properties   # DB config (EDIT for prod)
├── frontend/
│   ├── Dockerfile                        # node20 build → nginx:1.25-alpine, port 80
│   ├── nginx.conf                        # SPA + /api proxy (EDIT proxy target/domain/TLS)
│   ├── package.json
│   ├── angular.json
│   └── src/environments/environment.prod.ts   # apiUrl: '/api'
├── orange_db_dump.sql                    # <— to be generated from WAMP (see 7.1)
└── DEPLOYMENT.md                         # this document
```
