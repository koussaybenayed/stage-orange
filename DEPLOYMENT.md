# Production Deployment Documentation — Device Monitoring System

**Project:** Device Monitoring System (Orange DB monitoring)
**Git repository:** https://github.com/koussaybenayed/stage-orange (branch `main`)
**Last verified against source:** WAMP (local dev environment), August 2026

This document is intended for the **Infrastructure team** to deploy and host the application in production. It covers the database (`orange_db`), the backend (Spring Boot REST API), the frontend (Angular), and the deployment steps.

> **Note on the DB schema:** the database was recently reworked. Several new tables were added (`incident`, `incident_site`, `alarmes_all_log_nbi`, `famille`, `sous_famille`, `users`, `_tmp_excel_ms`, `re_u_n2_29_06_backup_20260813`) and some existing tables changed (`re_u_n2_29_06` gained `contact`/`X`/`Y`; `acsmaxbox_5g` moved to InnoDB with a reduced row count). Sections §4 and §5 below reflect the **current** schema and API.

> **Application update (this iteration):** the incident list (`/devices`) now shows a **Product class** column, and **RSRP 4G / SINR 4G / RSRP 5G / SINR 5G** are taken from the ACS row whose `timestamp` is **closest to the reclamation's creation date** (`re_u_n2_29_06.Créé_le`). A **« Product class »** filter was added to the list and a **« Répartition par product class »** doughnut chart to the dashboard. **No new database columns or tables were created for this feature** — it relies on columns already present in `acsmaxbox_5g` (`productclass`, `rsrp4g`, `sinr4g`, `rsrp5g`, `sinr5g`, `timestamp`). The only **new index requirement** is on `acsmaxbox_5g.IMSI` (see §4.3 and §8.4), which the new batch IMSI lookup used by these features depends on. This must be confirmed present in the production database (see §4.5 "DB confirmation checklist").

---

## 1. System Overview

| Component | What it does |
|---|---|
| **Frontend** | Angular 17 single-page app. Dashboard, device list/search, device detail, incident devices view, Home Zone / HZ-error analysis, and a Leaflet problem map. Served as static files behind Nginx. |
| **Backend** | Spring Boot 3.2 REST API. Reads device metrics (`acsmaxbox_5g`), incident records (`re_u_n2_29_06`), network incidents (`incident` + `incident_site`), HZ errors (`hz`), radio cell data, site coordinates (`site_otn`), and MSISDN↔IMSI lookups (`fixbox_combined_table`) from MySQL `orange_db`. Port **8081**. |
| **Database** | MySQL 8.0+ database named `orange_db` on WAMP (local) — must be recreated in production with the same schema/data. |

> The application is **read-heavy**. The largest table (`acsmaxbox_5g`) currently holds ~1.03M rows while `hz` (HZ network-status events) is the largest table by size. Plan disk and memory accordingly.

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
- Angular **17.x** (CLI 17.x), TypeScript **5.2**
- Chart.js **4.4** + ng2-charts **4.1**
- Leaflet **1.9.4** (problem map), RxJS **7.8**
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
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3306/orange_db
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=none          # schema is managed externally, NOT auto-created
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

Production must provide a real user/password (see section 6). `ddl-auto=none` means the schema must exist; the application never creates or alters tables.

### 4.2 Tables (measured on the source WAMP database, August 2026)

| Table | Engine | Rows (approx) | Purpose |
|---|---|---|---|
| `acsmaxbox_5g` | InnoDB | 1,031,827 | Device KPIs (main monitoring data) |
| `acsmaxbox_5g_new` | InnoDB | 0 | Staging/new device load (NOT used by app) |
| `acsmaxbox_latest_incidents` | MyISAM | 0 | Latest device snapshot per incident (deprecated — empty) |
| `alarmes_all_log_nbi` | MyISAM | 49,620 | Alarms from NBI (alarm log) |
| `etat_c_band` | MyISAM | 210 | C-band congestion state → action mapping |
| `famille` | InnoDB | 27 | Incident family taxonomy (groups `sous_famille`) |
| `fixbox_combined_table` | MyISAM | 625,555 | MSISDN ↔ IMSI lookup |
| `hz` | MyISAM | 0 (see §4.3) | Network handset status/error events |
| `incident` | InnoDB | 20,748 | Network incident records (management view) |
| `incident_site` | InnoDB | 31,266 | Incident ↔ site mapping (many-to-many join) |
| `lte_cell_info_lm_2026_06_30_11_32_27_244` | MyISAM | 29,283 | LTE cell reference data (huge flat file import) |
| `nr_cells` | MyISAM | 4,087 | 5G NR cell reference data |
| `re_u_n2_29_06` | MyISAM | 223 | Customer incident tickets ("Réunion" export) |
| `re_u_n2_29_06_backup_20260813` | MyISAM | 2,704 | Backup of `re_u_n2_29_06` snapshot (13 Aug 2026) |
| `site_otn` | MyISAM | 2,530 | Site names → GPS coordinates |
| `sous_famille` | InnoDB | 241 | Incident sub-family (FK → `famille`) |
| `users` | InnoDB | 0 | Application users (future auth; empty) |
| `_tmp_excel_ms` | MyISAM | 641 | Temporary MSISDN import helper |

> **Important sizing note:** `hz` (HZ network-status events) is the largest table by on-disk size (~1.85 GB of data + ~0.72 GB of indexes = ~2.6 GB total as currently stored), even though it currently holds **0 rows** in the working DB (its MyISAM files persist from the pre-rework load). `acsmaxbox_5g` is ~267 MB (227 MB data + 41 MB indexes at ~1.03M rows). Plan storage and memory accordingly, and provision at least 20 GB for a data-heavy `orange_db` overall.

### 4.3 Schema details

#### `acsmaxbox_5g` — device KPIs (main table, ~1.03M rows)
All signal columns are stored as `TEXT` (no numeric typing) — the app trims/parses them in Java.

| Column | Type | Notes |
|---|---|---|
| `SN` | text | **Logical primary key** (serial number) |
| `IMSI` | text | Contains trailing CR `\r`; app cleans with `.replace("\r","").trim()` |
| `id` | text | Another identifier (unused by app) |
| `deviceId` | text | Used in search |
| `productclass` | text | |
| `cellid` | text | Format `"eNodeBId-LocalCellId"` (e.g. `1234-5`), parsed by app |
| `pci4g`, `pci5g` | text | Physical Cell IDs (parsed as Double) |
| `rscp4g`, `rscp5g`, `rssi4g`, `rssi5g` | text | RSCP/RSSI metrics |
| `rsrp4g`, `rsrp5g` | text | RSRP metrics (4G / 5G) |
| `sinr4g`, `sinr5g` | text | SINR metrics |
| `rsrq4g`, `rsrq5g` | text | RSRQ metrics |
| `uplink_max_thrp`, `downlink_max_thrp` | text | Max throughput |
| `signalquality`, `linkquality` | text | |
| `date`, `hour`, `timestamp` | text | Measurement time (used for default sort `timestamp desc`) |
| `type` | text | |

> No primary-key constraint exists in MySQL; the entity treats `SN` as the `@Id`. The app queries this table with `LIMIT`/pagination and two IMSI-based lookups (see §5.4).

> **Feature dependency (product class, RSRP/SINR, closest-timestamp):** the incident enrichment reads `productclass`, `rsrp4g`, `sinr4g`, `rsrp5g`, `sinr5g`, `cellid`, `pci5g` and the measurement time columns `timestamp` / `date` / `hour` from this table. To pick the row closest to a reclamation date, the backend compares the string `timestamp` (`yyyy-MM-dd HH:mm:ss`) against `re_u_n2_29_06.Créé_le`. **Must be confirmed in production:** these columns exist and are populated, `timestamp` stays in `yyyy-MM-dd HH:mm:ss` format, and an index on `IMSI` is present for the batch lookup (see §4.5).

#### `re_u_n2_29_06` — customer incident tickets (223 rows)
Columns: `Numéro_de_la_demande` (text), `Créé_le` (datetime), `Sujet` (text), `Description` (text), `MSISDN_concerné` (text), `Offre__Contrat` (text), plus the new columns **`contact`** (varchar(30)) and **`X` / `Y`** (double).

- The app's entity (`ReUn22906`) maps `Numéro_de_la_demande` → `requestNumber` (the `@Id`), `Créé_le` → `created`, `MSISDN_concerné` → `msisdn` (a `Long`; the column is `TEXT`, so production data must contain numeric strings only), and the new `contact`, `X`, `Y` fields.
- Related incident analysis still filters `Sujet` for "Déconnexion", "Echec de connexion", or "Lenteur" and `Offre__Contrat` for "MAXBOX 5G".
- `re_u_n2_29_06_backup_20260813` is a frozen backup of the pre-rework row set (2,704 rows) and is **not** used by the application.

#### `incident` + `incident_site` — network incident management (NEW)
The app correlates device/site data with **operational network incidents** through a join of these two tables via `JdbcTemplate`:

- `incident` (20,748 rows, InnoDB): `Id_incident` (PK), `Date_debut`, `Date_fin`, `Nb_2G`, `Nb_3G`, `Nb_4G`, `Nb_4G_TDD`, `Nb_5G`, `services` (e.g. `4G/5G`), `Type_incident`, `cause`, `action`, `TOC`, `Duree`, `initiateur_id`, `fermer_par_id`, `FII_par_id`, `modifier_par_id`, `cause_depassement_id`, `id_sous_famille` (FK → `sous_famille`), `Semaine`, `Mois`, `An`, `nb_sites`, `Criticite`, `cause_alerte`, `crise`, `action_chez`, `mails`, `sms_envoye`, `sms_fin`, `MAJ`, `rai`, `T_incident`, `updated_at`, `created_at`, plus creation/closure timestamps.
- `incident_site` (31,266 rows, InnoDB): composite PK `(Incident_Id, Site_Code)`, plus `Zone`, `updated_at`, `created_at`.

The service loads `(Site_Code → list of [Date_debut, Date_fin, services])` into an in-memory cache and, given a device's site prefix and a reclamation date, flags `hasIncident` / `incidentPeriod` / `incidentTech`. `nearby-sites` uses the same cache to report whether a map site had an incident.

#### `alarmes_all_log_nbi` — NBI alarms (NEW, 49,620 rows)
Columns: `Id_alarme` (PK), `code_site`, `nom_alarme`, `date`, `detail`, `criticite`, `source`, `comment`, `date_insertion`, `Info_blocage`, `Info_blocage2`, `Deblocage`, `Blocage`, `Blocage2`, `marquage_statique`, `users`, `date_fin`, `date_creation_toc`. Not currently referenced by backend services but part of the production data model.

#### `famille` / `sous_famille` — taxonomy (NEW)
- `famille` (27 rows): `id_famille` (PK), `Nom_Famille`.
- `sous_famille` (241 rows): `id_sous_famille` (PK), `Nom_sous_famille`, `id_famille` (FK → `famille`).
The `incident` table references `sous_famille` for classification.

#### `users` — application users (NEW, empty)
`id` (PK), `name`, `login` (unique), `login_verified_at`, `password`, `remember_token`, `created_at`, `updated_at`, `cuid`, `phone`, `random_code`, `profile`, `logout_at`. Reserved for future authentication; no auth is currently enforced by the backend.

#### `hz` — network status/error events (see sizing note)
Columns: `Time` (text), `MSISDN`, `IMSI`, `IMSI_2`, `Site_ID`, `site_Name`, `error_code`, `status`, `code_resv1`, `code_resv2`, `APN`.
**Mapping rule used by the app:** `hz.MSISDN = original MSISDN + 21600000000` (`HZ_MSISDN_OFFSET`). HZ windows use ±3 days (`HZ_WINDOW_DAYS`), error types are the set of statuses in `HZ_ERROR_TYPES`, and the daily-evolution analysis looks back `HZ_DAILY_DAYS` (5) days.

#### `fixbox_combined_table` — MSISDN ↔ IMSI (625,555 rows)
Columns: `MSISDN` (bigint, indexed `MUL`), `IMSI` (bigint). The `FixboxCombinedTableRepository.findImsiByMsisdn(fullMsisdn)` query maps an incident/HZ MSISDN (with the 216 offset) to a device IMSI.

#### `lte_cell_info_lm_2026_06_30_11_32_27_244` — LTE cell reference (29,283 rows)
Huge imported sheet (60+ columns: `eNodeB_Id`, `Local_cell_identity`, `Cell_Name`, `Cell_ID`, `Physical_cell_ID`, `Frequency_band`, …). The app uses `eNodeB_Id` + `Local_cell_identity` to resolve a `Cell_Name` for a device's `cellid`. The `LteCellInfo` entity exposes only `Cell_Name` (Id), `enodeBID`, and `Local_cell_identity`.

#### `nr_cells` — 5G NR cells (4,087 rows)
Columns: `Subarea`, `RAT`, `Operator`, `gNodeB_ID`, `NR_NE_Name`, `gNodeB_Function_Name`, `NE_Connection_Status`, `NR_Cell_ID`, `Cell_Name`, `Cell_ID`, `TAC`, `Frequency_Band`, `Physical_Cell_ID`, `DLNARFCN`, `Administrative_Status`, `Activation_Status`, `Operating_Status`, `Availability_Status`, `clé`, … The app maps `clé` (key = `prefix + PCI5G`) → `Cell_Name` to find the 5G cell name for a device.

#### `site_otn` — site coordinates (2,530 rows)
Columns: `site`, `Longitude_Sector` (double), `Latitude_Sector` (double), `CoverageType`. App caches `site → [lat, lng]` in memory at startup/first use.

#### `etat_c_band` — congestion/action lookup (210 rows)
Columns: `Sites`, `sect`, `Étiquettes_de_lignes` (text), `Moyenne_de_NR_DL_User_Throughput_(Mbps)` (double), `Action` (text). App loads `Étiquettes_de_lignes → Action` to mark a 5G cell as "congestionnée".

#### `acsmaxbox_5g_new` / `acsmaxbox_latest_incidents` / `_tmp_excel_ms`
- `acsmaxbox_5g_new`: identical schema to `acsmaxbox_5g`; not used by the application (empty).
- `acsmaxbox_latest_incidents`: identical columns to `acsmaxbox_5g`; the old native query that used it is no longer referenced — now empty.
- `_tmp_excel_ms`: single-column (`ms` bigint PK) temporary import helper; not used by the application.

### 4.4 How MSISDN → device lookup works (business rule)
1. Incident (or HZ event) gives an MSISDN (e.g. `4123456789`).
2. Backend builds the full network MSISDN: `fullMsisdn = 21600000000 + msisdn`.
3. `fixbox_combined_table.findImsiByMsisdn(fullMsisdn)` → device IMSI.
4. `IMSI` (after stripping `\r`) is matched against `acsmaxbox_5g` (`findAllByImsiIn` / `findByImsiAndRsrp5GIsNotNull`).
5. **For the incident list (`/devices`), the backend selects the ACS row whose `timestamp` is closest to the reclamation's creation date (`re_u_n2_29_06.Créé_le`)** — `selectClosestDevice()`. The **Product class**, **RSRP 4G / SINR 4G / RSRP 5G / SINR 5G**, and the `cellid` are read from that chosen row.
6. Device's `cellid` (`eNodeBId-LocalCellId`) is resolved through `lte_cell_info...` to a cell name, then to a site prefix (first 8 chars) → coordinates from `site_otn`, and 5G cell via `nr_cells` (`prefix + PCI5G`). Congestion is looked up in `etat_c_band`.

### 4.5 DB confirmation checklist (required in production for the current features)
Confirm the following before/after importing the dump — these guarantee the Product class, RSRP/SINR, cell-name, and closest-timestamp features work:

```sql
-- 1) Columns used by the incident enrichment exist and are populated:
SELECT column_name FROM information_schema.columns
WHERE table_schema='orange_db' AND table_name='acsmaxbox_5g'
  AND column_name IN ('productclass','rsrp4g','sinr4g','rsrp5g','sinr5g','cellid','pci5g','timestamp','date','hour');

-- Product class must contain non-empty values; sample check:
SELECT DISTINCT productclass FROM acsmaxbox_5g WHERE productclass IS NOT NULL AND productclass <> '';

-- 2) timestamp format is 'yyyy-MM-dd HH:mm:ss' (used for closest-date matching):
SELECT COUNT(*) FROM acsmaxbox_5g
WHERE timestamp IS NOT NULL AND timestamp NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$';

-- 3) Index on IMSI (essential for the batch lookup findAllByImsiIn used by /devices, /top-zones,
--    /stats/by-product-class and /hz/msisdn/*). Create if missing:
ALTER TABLE acsmaxbox_5g ADD INDEX idx_imsi (`IMSI`(20));

-- 4) re_u_n2_29_06.Créé_le is a datetime — used as the comparison target:
SELECT column_type FROM information_schema.columns
WHERE table_schema='orange_db' AND table_name='re_u_n2_29_06' AND column_name='Créé_le';

-- 5) Reference tables (cell names, sites, congestion) are populated and are NOT refreshed automatically:
--    restart the backend after importing/updating lte_cell_info_..., nr_cells, site_otn, etat_c_band.
SELECT COUNT(*) FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE Cell_Name IS NOT NULL;
SELECT COUNT(*) FROM nr_cells WHERE Cell_Name IS NOT NULL;
SELECT COUNT(*) FROM site_otn WHERE site IS NOT NULL;
SELECT COUNT(*) FROM etat_c_band WHERE `Étiquettes_de_lignes` IS NOT NULL;
```

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
| Method | Path | Description | Query params |
|---|---|---|---|
| GET | `/api/incidents` | All incidents ordered by `created` desc | — |
| GET | `/api/incidents/with-device-info` | Incidents enriched with device KPIs, cell names, coordinates, HZ errors, incident-site correlation | `msisdn` (optional filter) |
| GET | `/api/incidents/stats/overview` | `{ totalIncidents, lastDay, last7Days }` | — |
| GET | `/api/incidents/stats/by-type` | Count by `Sujet` | — |
| GET | `/api/incidents/stats/by-offre` | Count by `Offre__Contrat` | — |
| GET | `/api/incidents/stats/by-product-class` | Count by ACS `productclass` (uses closest-to-reclamation-date ACS row per incident) | — |
| GET | `/api/incidents/stats/by-date` | Count by date | — |
| GET | `/api/incidents/stats/hzerror` | HZ error distribution per incident (within ±3 days) | — |
| GET | `/api/incidents/hz/daily-evolution` | HZ error types count per day (last 5 days) | `apn` (optional filter) |
| GET | `/api/incidents/hz/offers` | HZ rows grouped by APN (last 5 days) | — |
| GET | `/api/incidents/hz/errors` | HZ error rows for a date/status (grouped by MSISDN) | `date`, `status`, `apn`, `limit` (200) |
| GET | `/api/incidents/nearby-sites` | Sites within radius with optional incident info | `lat`, `lng`, `radius` (5000), `date` |
| GET | `/api/incidents/top-zones` | Top congested/site zones | `limit` (10) |
| GET | `/api/incidents/hz/msisdn/{msisdn}` | HZ stats for one MSISDN (status counts + recent errors), enriched with device/site info | `dateFrom`, `dateTo` |
| GET | `/api/incidents/hz/msisdn/{msisdn}/daily-evolution` | HZ daily series for one MSISDN | `dateFrom`, `dateTo` |

### 5.4 Important backend behaviors the infra team should know
- **CORS is wide open** (`allowedOrigins("*")` in `DeviceMonitoringApplication.java`) — fine for a stage app; restrict in production.
- **In-memory reference caches** (`site_otn`, `lte_cell_info…`, `nr_cells`, `etat_c_band`, and the `incident`+`incident_site` join) are loaded once per JVM on first use and never refreshed — **restart the backend after updating these tables**.
- **HZ latest-date cache**: `resolveHzLatestDate()` caches `MAX(hz.Time)` for 1 hour (`HZ_LATEST_CACHE_TTL_MS`) and is pre-warmed on startup in a daemon thread.
- Several heavy endpoints (`/with-device-info`, `/top-zones`, `/stats/hzerror`, `/stats/by-product-class`, `/hz/msisdn/*`) compute in Java memory and fire multiple SQL queries (`hz`, `fixbox_combined_table`, `acsmaxbox_5g`) plus full reference-cache loads. They are not cheap — expect higher CPU/latency.
- `HZ_ERROR_TYPES` filtering uses status values `EGCI not in Home Zone`, `ECGI Not authorized`, `TAC not allowed`, `Temporarily Blocked`, `IMEI_TAC not allowed`, `Session rejected`. The strict status `EGCI not in Home Zone` needs ≥3 occurrences (`HZ_ERROR_THRESHOLD`) to count.
- The `hz` window uses string timestamps (`yyyy-MM-dd HH:mm:ss`); keep that format in prod data.
- `searchDevices` / default sort reference `timestamp`, so index/sort by `timestamp` in MySQL for acceptable performance.
- The `hz` `Time` column is a `TEXT` string that is parsed as a `DATE(...)`. Ensure it stays numeric/`yyyy-MM-dd HH:mm:ss` formatted.

---

## 6. Frontend — Angular SPA

### 6.1 Build & run (dev)
```bash
cd frontend
npm install
npm start          # ng serve --proxy-config proxy.conf.json  → http://localhost:4200
```
The dev proxy (`proxy.conf.json`) sends `/api` → `http://localhost:8081`.

### 6.2 Routes / views
- `/dashboard` — overview cards, incident stats, HZ charts, and the **« Répartition par product class »** doughnut chart
- `/home-zone` — Home Zone / APN analysis
- `/hz-errors` — detailed HZ error exploration (date/status/APN drill-down)
- `/devices` — incident list with **Product class** column + « Product class » filter, device search
- `/devices/by-msisdn/:msisdn` — device(s) for a given MSISDN
- `/devices/:id` — device detail / edit
- `/problem-map` — Leaflet map with problem sites (`nearby-sites` / `top-zones`)

### 6.3 Production build
```bash
cd frontend
npm run build:prod          # = ng build --configuration production
# output: dist/device-monitoring-frontend/ (static files)
```
- `src/environments/environment.prod.ts` → `apiUrl: '/api'` (same-origin relative URL).
- The SPA is designed to sit **behind Nginx on the same origin**, with Nginx proxying `/api/` to the backend. There is **no hardcoded backend host** in the production bundle.

### 6.4 Nginx config (already provided — `frontend/nginx.conf`)
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

> ⚠️ The 29M-row `acsmaxbox_5g` from earlier snapshots can make the dump large/slow. In the current working DB `acsmaxbox_5g` is ~1.03M rows, but the on-disk tables (`hz`, `acsmaxbox_5g`) may still be large. Use `--single-transaction` for a consistent snapshot and consider `--quick` + gzip (`| gzip`) to speed transfer.

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
- [ ] Decide the intended `hz` data load. The working DB has `hz` empty while its on-disk size is large; production must either load real HZ data or purge/compact the table before shipping.

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
- `acsmaxbox_5g` has **no index on `timestamp`** — the default sort (`timestamp,desc`) may cause slow scans on larger datasets. Create indexes. **The `idx_imsi` index is now REQUIRED** — the Product class / RSRP / SINR / closest-timestamp enrichment (`/with-device-info`, `/stats/by-product-class`, `/top-zones`, `/hz/msisdn/*`) batch-queries all rows for an IMSI via `findAllByImsiIn`; without it the feature degrades to a full table scan on ~1M rows:
  ```sql
  ALTER TABLE acsmaxbox_5g ADD INDEX idx_timestamp (`timestamp`);
  ALTER TABLE acsmaxbox_5g ADD INDEX idx_imsi (`IMSI`(20));   -- REQUIRED for new features
  ALTER TABLE acsmaxbox_5g ADD INDEX idx_cellid (`cellid`(20));
  ```
- `fixbox_combined_table.MSISDN` is already indexed (`MUL`) — the hot IMSI lookup relies on it. Keep that index and consider one on `IMSI`.
- Consider migrating the large/join MyISAM tables (`hz`, `fixbox_combined_table`, reference tables) to **InnoDB** for crash safety and row-level locking. Test performance first (MyISAM was likely chosen for import speed).
- `acsmaxbox_5g_new` is a duplicate staging table not used by the app — exclude it from the prod migration if not needed.
- Table names are case-sensitive on Linux (e.g. `re_u_n2_29_06`, `lte_cell_info_lm_2026_06_30_11_32_27_244`). Match exactly.

---

## 9. Operational / Troubleshooting Notes

| Symptom | Likely cause / fix |
|---|---|
| Backend fails to start | DB not reachable, wrong credentials, or `orange_db` not imported. Check `SPRING_DATASOURCE_*`. |
| 404 on `/api/...` from Nginx | `proxy_pass` target wrong, or backend not listening on 8081. |
| Slow device list | Missing `timestamp` index on `acsmaxbox_5g`. |
| Blank page in production | Nginx `try_files` missing (must fall back to `/index.html` for SPA routing). |
| Incidents show no device info | MSISDN → IMSI mapping (`21600000000 + msisdn`) produces no match in `fixbox_combined_table`, or the IMSI has no row in `acsmaxbox_5g`. |
| Product class column / chart empty | `acsmaxbox_5g.productclass` missing/empty, or the IMSI batch lookup has no index (`idx_imsi`) making it slow/time-out, or the MSISDN→IMSI mapping is missing. |
| RSRP/SINR not matching the reclamation date | `timestamp` column empty or not in `yyyy-MM-dd HH:mm:ss`, or reclamation `Créé_le` not a datetime. |
| HZ errors empty | `hz` table empty, timestamps not `yyyy-MM-dd HH:mm:ss`, or MSISDN offset (21600000000) mismatch. |
| `hasIncident` / incidents on map empty | `incident` / `incident_site` tables empty, or incident `Date_debut` doesn't match the reclamation date; `services` returned by `resolveTechLabel`. |
| Map shows no markers | `site_otn` empty or `site` prefix (first 8 chars of cell name) not present in `site_otn`. |
| Stale cell/site/incident info | Reference caches load once per JVM; restart backend after updating `lte_cell_info…`, `nr_cells`, `site_otn`, `etat_c_band`, `incident`, `incident_site`. |

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
