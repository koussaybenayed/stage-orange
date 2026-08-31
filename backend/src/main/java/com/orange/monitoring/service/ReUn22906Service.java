package com.orange.monitoring.service;

import com.orange.monitoring.dto.HzDailyPoint;
import com.orange.monitoring.dto.HzDailySeries;
import com.orange.monitoring.dto.HzError;
import com.orange.monitoring.dto.HzMsisdnStats;
import com.orange.monitoring.dto.IncidentOverview;
import com.orange.monitoring.dto.IncidentWithDeviceInfo;
import com.orange.monitoring.dto.NameCount;
import com.orange.monitoring.dto.NearbySite;
import com.orange.monitoring.dto.TopZonesResponse;
import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.entity.NrCell;
import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.entity.SiteOtn;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import com.orange.monitoring.repository.FixboxCombinedTableRepository;
import com.orange.monitoring.repository.LteCellInfoRepository;
import com.orange.monitoring.repository.ReUn22906Repository;
import com.orange.monitoring.repository.SiteOtnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ReUn22906Service {

    @Autowired
    private ReUn22906Repository repository;

    @Autowired
    private FixboxCombinedTableRepository fixboxRepository;

    @Autowired
    private AcsMaxBox5GRepository acsRepository;

    @Autowired
    private LteCellInfoRepository lteCellInfoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteOtnRepository siteOtnRepository;

    @PostConstruct
    public void warmHzLatestDate() {
        Thread warm = new Thread(() -> {
            try {
                resolveHzLatestDate();
            } catch (Exception ignored) {
            }
        }, "hz-latest-warmup");
        warm.setDaemon(true);
        warm.start();
    }

    private Map<String, Double[]> siteCache; // sitePrefix -> [lat, lng]
    private Map<String, String> lteCellCache; // "eNodeBId_localCellId" -> cellName
    private Map<String, String> nrCellCache; // cle -> cellName
    private Map<String, String> etatCBandCache; // cell name -> action (etat_c_band)
    private Map<String, List<IncidentPeriod>> incidentSiteCache; // siteCode -> incidents (Date_debut, Date_fin)

    private static final long HZ_MSISDN_OFFSET = 21600000000L;
    private static final int HZ_WINDOW_DAYS = 3;
    private static final int HZ_ERROR_THRESHOLD = 3;
    private static final String HZ_STRICT_STATUS = "EGCI not in Home Zone";
    private static final int HZ_DAILY_DAYS = 5;
    private static final long HZ_LATEST_CACHE_TTL_MS = 60 * 60 * 1000L;

    private volatile String hzLatestDateCache;
    private volatile long hzLatestDateCacheTs;

    private static final long HZ_DAILY_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final long HZ_OFFER_CACHE_TTL_MS = 5 * 60 * 1000L;
    private final Map<String, CachedHzDaily> hzDailyCache = new ConcurrentHashMap<>();
    private final Map<String, CachedNameCounts> hzOfferCache = new ConcurrentHashMap<>();

    private static final List<String> HZ_ERROR_TYPES = Arrays.asList(
            "EGCI not in Home Zone",
            "ECGI Not authorized",
            "TAC not allowed",
            "Temporarily Blocked",
            "IMEI_TAC not allowed",
            "Session rejected"
    );

    private static class IncidentPeriod {
        final LocalDateTime debut;
        final LocalDateTime fin;
        final String services;
        IncidentPeriod(LocalDateTime debut, LocalDateTime fin, String services) {
            this.debut = debut;
            this.fin = fin;
            this.services = services;
        }
    }

    private static final class CachedHzDaily {
        final long ts;
        final List<HzDailySeries> value;
        CachedHzDaily(long ts, List<HzDailySeries> value) {
            this.ts = ts;
            this.value = value;
        }
    }

    private static final class CachedNameCounts {
        final long ts;
        final List<NameCount> value;
        CachedNameCounts(long ts, List<NameCount> value) {
            this.ts = ts;
            this.value = value;
        }
    }

    public List<ReUn22906> getFilteredIncidents() {
        return repository.findAllOrderByCreatedDesc();
    }

    public List<ReUn22906> getFilteredIncidentsBySujet(String sujet) {
        if (sujet == null || sujet.trim().isEmpty()) {
            return getFilteredIncidents();
        }
        return repository.findBySujetContainingIgnoreCaseOrderByCreatedDesc(sujet.trim());
    }

    public List<IncidentWithDeviceInfo> getIncidentsWithDeviceInfo() {
        return getIncidentsWithDeviceInfo(null);
    }

    public List<IncidentWithDeviceInfo> getIncidentsWithDeviceInfo(Long msisdn) {
        List<ReUn22906> incidents = getFilteredIncidents();
        if (msisdn != null) {
            incidents = incidents.stream()
                    .filter(inc -> msisdn.equals(inc.getMsisdn()))
                    .collect(Collectors.toList());
        }

        Map<Long, List<AcsMaxBox5G>> devicesByOriginalMsisdn = buildDevicesByMsisdn(incidents);
        Map<Long, String> hzErrorByMsisdn = buildHzErrors(incidents);

        loadCaches();

        List<IncidentWithDeviceInfo> result = new ArrayList<>();
        for (ReUn22906 inc : incidents) {
            IncidentWithDeviceInfo info = new IncidentWithDeviceInfo();
            info.setRequestNumber(inc.getRequestNumber());
            info.setCreated(inc.getCreated());
            info.setSujet(inc.getSujet());
            info.setDescription(inc.getDescription());
            info.setContact(inc.getContact());
            info.setMsisdn(inc.getMsisdn());
            info.setOffreContrat(inc.getOffreContrat());
            info.setX(inc.getX());
            info.setY(inc.getY());

            if (inc.getMsisdn() != null) {
                info.setHzError(hzErrorByMsisdn.get(inc.getMsisdn()));

                List<AcsMaxBox5G> devices = devicesByOriginalMsisdn.get(inc.getMsisdn());
                if (devices != null && !devices.isEmpty()) {
                    AcsMaxBox5G device = selectClosestDevice(devices, inc.getCreated());
                    try { info.setDebugImsi(Long.parseLong(device.getImsi().replace("\r", "").trim())); } catch (Exception e) { /* ignore */ }
                    info.setProductClass(device.getProductclass());
                    info.setRsrp4G(device.getRsrp());
                    info.setSinr4G(device.getSinr());
                    info.setRsrp5G(device.getRsrp5G());
                    info.setSinr5G(device.getSinr5G());
                    resolveCellInfoCached(device, info);
                    info.setCongestionnee(isCongestionnee(info.getCellName5G()));
                    if (info.isCongestionnee()) {
                        info.setAction(actionFor(info.getCellName5G()));
                    }
                }
            }

            resolveIncidentForSite(info, inc.getCreated());

            result.add(info);
        }
        return result;
    }

    private void loadCaches() {
        if (siteCache == null) {
            Map<String, Double[]> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT site, Latitude_Sector, Longitude_Sector FROM site_otn WHERE site IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    String site = row.get("site").toString();
                    Double lat = row.get("Latitude_Sector") != null ? ((Number) row.get("Latitude_Sector")).doubleValue() : null;
                    Double lng = row.get("Longitude_Sector") != null ? ((Number) row.get("Longitude_Sector")).doubleValue() : null;
                    if (lat != null && lng != null) {
                        map.put(site, new Double[]{lat, lng});
                    }
                }
            } catch (Exception ignored) {}
            siteCache = map;
        }
        if (lteCellCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT eNodeB_Id, Local_cell_identity, Cell_Name FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE Cell_Name IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    String key = row.get("eNodeB_Id") + "_" + row.get("Local_cell_identity");
                    map.put(key, row.get("Cell_Name").toString());
                }
            } catch (Exception ignored) {}
            lteCellCache = map;
        }
        if (nrCellCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT cl\u00e9, Cell_Name FROM nr_cells WHERE Cell_Name IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    map.put(row.get("cl\u00e9").toString(), row.get("Cell_Name").toString());
                }
            } catch (Exception ignored) {}
            nrCellCache = map;
        }
        if (etatCBandCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT `\u00c9tiquettes_de_lignes`, `Action` FROM etat_c_band WHERE `\u00c9tiquettes_de_lignes` IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    Object v = row.get("\u00c9tiquettes_de_lignes");
                    if (v != null) {
                        map.put(v.toString().trim(), row.get("Action") != null ? row.get("Action").toString() : null);
                    }
                }
            } catch (Exception ignored) {}
            etatCBandCache = map;
        }
        if (incidentSiteCache == null) {
            Map<String, List<IncidentPeriod>> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT s.Site_Code, i.Date_debut, i.Date_fin, i.services " +
                    "FROM Incident i JOIN Incident_Site s ON s.Incident_Id = i.Id_incident " +
                    "WHERE s.Site_Code IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    String siteCode = row.get("Site_Code").toString();
                    LocalDateTime debut = toLocalDateTime(row.get("Date_debut"));
                    LocalDateTime fin = toLocalDateTime(row.get("Date_fin"));
                    String services = row.get("services") != null ? row.get("services").toString() : null;
                    if (debut == null) continue;
                    map.computeIfAbsent(siteCode, k -> new ArrayList<>())
                       .add(new IncidentPeriod(debut, fin, services));
                }
                for (List<IncidentPeriod> list : map.values()) {
                    list.sort(Comparator.comparing(p -> p.debut));
                }
            } catch (Exception ignored) {}
            incidentSiteCache = map;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) value).toLocalDateTime();
            }
            if (value instanceof java.util.Date) {
                return new java.sql.Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
            }
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isCongestionnee(String cellName5G) {
        return cellName5G != null && etatCBandCache != null && etatCBandCache.containsKey(cellName5G.trim());
    }

    private String actionFor(String cellName5G) {
        if (cellName5G == null || etatCBandCache == null) {
            return null;
        }
        return etatCBandCache.get(cellName5G.trim());
    }

    private Map<Long, List<AcsMaxBox5G>> buildDevicesByMsisdn(List<ReUn22906> incidents) {
        // Collect original MSISDNs from incidents
        List<Long> originalMsisdns = incidents.stream()
                .filter(inc -> inc.getMsisdn() != null)
                .map(ReUn22906::getMsisdn)
                .distinct()
                .collect(Collectors.toList());

        if (originalMsisdns.isEmpty()) {
            return Collections.emptyMap();
        }

        // IMSI comes from fixbox_combined_table, matched on the full MSISDN (216 prefix + incident MSISDN)
        Map<Long, String> imsiByOriginalMsisdn = new HashMap<>();
        List<String> imsis = new ArrayList<>();
        for (Long m : originalMsisdns) {
            Long fullMsisdn = HZ_MSISDN_OFFSET + m;
            Optional<Long> imsiOpt = fixboxRepository.findImsiByMsisdn(fullMsisdn);
            if (imsiOpt.isPresent()) {
                String imsi = imsiOpt.get().toString();
                imsiByOriginalMsisdn.put(m, imsi);
                imsis.add(imsi);
            }
        }

        // Batch-fetch devices from acsmaxbox_5g (1 indexed query)
        List<AcsMaxBox5G> allDevices = imsis.isEmpty() ? Collections.emptyList() : acsRepository.findAllByImsiIn(imsis);

        // Build: IMSI -> list of devices (group by cleaned IMSI, same key the SQL partitions on)
        Map<String, List<AcsMaxBox5G>> devicesByImsi = allDevices.stream()
                .filter(d -> d.getImsi() != null)
                .collect(Collectors.groupingBy(d -> d.getImsi().replace("\r", "").trim()));

        // Build: original MSISDN -> list of devices (prefer device with rsrp5G)
        Map<Long, List<AcsMaxBox5G>> result = new HashMap<>();
        for (Map.Entry<Long, String> entry : imsiByOriginalMsisdn.entrySet()) {
            Long originalMsisdn = entry.getKey();
            List<AcsMaxBox5G> devices = devicesByImsi.get(entry.getValue());
            if (devices != null) {
                // Sort so device with rsrp5G comes first if available
                devices.sort((a, b) -> {
                    boolean aHas5g = a.getRsrp5G() != null && !a.getRsrp5G().isEmpty();
                    boolean bHas5g = b.getRsrp5G() != null && !b.getRsrp5G().isEmpty();
                    if (aHas5g && !bHas5g) return -1;
                    if (!aHas5g && bHas5g) return 1;
                    return 0;
                });
                result.put(originalMsisdn, devices);
            }
        }
        return result;
    }

    /**
     * Picks the ACS row whose timestamp is the closest to the reclamation's
     * creation date. Falls back to the first device if timestamps cannot be parsed.
     */
    private AcsMaxBox5G selectClosestDevice(List<AcsMaxBox5G> devices, String reclamationCreated) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        if (devices.size() == 1) {
            return devices.get(0);
        }
        long target = toEpoch(reclamationCreated);
        if (target == Long.MIN_VALUE) {
            return devices.get(0);
        }
        AcsMaxBox5G best = null;
        long bestDiff = Long.MAX_VALUE;
        for (AcsMaxBox5G d : devices) {
            long ts = toEpoch(d.getTimestamp());
            if (ts == Long.MIN_VALUE) {
                continue;
            }
            long diff = Math.abs(ts - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = d;
            }
        }
        return best != null ? best : devices.get(0);
    }

    private static long toEpoch(String value) {
        if (value == null || value.isEmpty()) {
            return Long.MIN_VALUE;
        }
        try {
            return java.time.LocalDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(value.trim()).atStartOfDay()
                        .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            } catch (Exception e2) {
                return Long.MIN_VALUE;
            }
        }
    }

    public IncidentOverview getOverview() {
        IncidentOverview overview = new IncidentOverview();
        overview.setTotalIncidents(repository.countFilteredIncidents());
        overview.setLastDay(repository.countLastDayIncidents());
        overview.setLast7Days(repository.countLast7DaysIncidents());
        return overview;
    }

    public List<NameCount> getTypeDistribution() {
        return toNameCounts(repository.countByType());
    }

    public List<NameCount> getOffreDistribution() {
        return toNameCounts(repository.countByOffre());
    }

    public List<NameCount> getProductClassDistribution() {
        List<ReUn22906> incidents = getFilteredIncidents();
        Map<Long, List<AcsMaxBox5G>> devicesByMsisdn = buildDevicesByMsisdn(incidents);

        Map<String, Long> counts = new HashMap<>();
        for (ReUn22906 inc : incidents) {
            if (inc.getMsisdn() == null) {
                continue;
            }
            List<AcsMaxBox5G> devices = devicesByMsisdn.get(inc.getMsisdn());
            if (devices == null || devices.isEmpty()) {
                continue;
            }
            AcsMaxBox5G device = selectClosestDevice(devices, inc.getCreated());
            String productClass = device.getProductclass();
            if (productClass != null && !productClass.trim().isEmpty()) {
                counts.merge(productClass.trim(), 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public List<NameCount> getDateDistribution() {
        return toNameCounts(repository.countByDate());
    }

    private List<NameCount> toNameCounts(List<Object[]> rows) {
        List<NameCount> result = new ArrayList<>();
        for (Object[] row : rows) {
            String name = row[0] != null ? row[0].toString() : "Inconnu";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            result.add(new NameCount(name, count));
        }
        return result;
    }

    public List<NameCount> getHzErrorDistribution() {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : fetchHzCounts()) {
            counts.merge(row.get("status").toString(), ((Number) row.get("cnt")).longValue(), Long::sum);
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public List<HzDailySeries> getHzDailyEvolution(String apn) {
        String key = apn == null ? "all" : apn.trim().isEmpty() ? "all" : apn.trim();
        long now = System.currentTimeMillis();
        CachedHzDaily cached = hzDailyCache.get(key);
        if (cached != null && (now - cached.ts) < HZ_DAILY_CACHE_TTL_MS) {
            return cached.value;
        }
        synchronized (this) {
            cached = hzDailyCache.get(key);
            if (cached != null && (now - cached.ts) < HZ_DAILY_CACHE_TTL_MS) {
                return cached.value;
            }
            List<HzDailySeries> result = queryHzDailyEvolution(null, apn);
            if (result.isEmpty()) {
                String anchor = resolveHzLatestDate();
                if (anchor != null) {
                    result = queryHzDailyEvolution(anchor, apn);
                }
            }
            hzDailyCache.put(key, new CachedHzDaily(System.currentTimeMillis(), result));
            return result;
        }
    }

    private List<HzDailySeries> queryHzDailyEvolution(String anchorDate, String apn) {
        String anchorExpr = anchorDate == null ? "CURRENT_DATE" : "DATE('" + anchorDate + "')";

        StringBuilder inList = new StringBuilder();
        for (int i = 0; i < HZ_ERROR_TYPES.size(); i++) {
            if (i > 0) inList.append(",");
            inList.append("'").append(HZ_ERROR_TYPES.get(i).replace("'", "''")).append("'");
        }

        String apnClause = "";
        List<Object> args = new ArrayList<>();
        if (apn != null && !apn.trim().isEmpty() && !apn.trim().equalsIgnoreCase("all")) {
            apnClause = " AND h.APN = ? ";
            args.add(apn.trim());
        }

        String sql = "SELECT day, status, COUNT(*) AS devices FROM ("
                + "SELECT DATE(h.`Time`) AS day, h.status, h.MSISDN "
                + "FROM hz h "
                + "WHERE h.status IN (" + inList + ")"
                + "AND h.`Time` >= DATE_FORMAT(" + anchorExpr + " - INTERVAL " + (HZ_DAILY_DAYS - 1) + " DAY, '%Y-%m-%d 00:00:00') "
                + "AND h.`Time` < DATE_FORMAT(" + anchorExpr + " + INTERVAL 1 DAY, '%Y-%m-%d 00:00:00') "
                + apnClause
                + "GROUP BY DATE(h.`Time`), h.status, h.MSISDN "
                + "HAVING COUNT(*) >= CASE WHEN h.status = '" + HZ_STRICT_STATUS + "' THEN " + HZ_ERROR_THRESHOLD + " ELSE 1 END) t "
                + "GROUP BY day, status "
                + "ORDER BY status, day";

        List<Map<String, Object>> rows = args.isEmpty()
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, args.toArray());

        Map<String, List<HzDailyPoint>> seriesByStatus = new LinkedHashMap<>();
        List<String> statusOrder = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String status = row.get("status").toString();
            String date = row.get("day").toString();
            long devices = ((Number) row.get("devices")).longValue();
            if (!seriesByStatus.containsKey(status)) {
                seriesByStatus.put(status, new ArrayList<>());
                statusOrder.add(status);
            }
            seriesByStatus.get(status).add(new HzDailyPoint(date, devices));
        }

        List<HzDailySeries> result = new ArrayList<>();
        for (String status : statusOrder) {
            result.add(new HzDailySeries(status, seriesByStatus.get(status)));
        }
        return result;
    }

    public List<NameCount> getHzOfferDistribution() {
        long now = System.currentTimeMillis();
        CachedNameCounts cached = hzOfferCache.get("all");
        if (cached != null && (now - cached.ts) < HZ_OFFER_CACHE_TTL_MS) {
            return cached.value;
        }
        synchronized (this) {
            cached = hzOfferCache.get("all");
            if (cached != null && (now - cached.ts) < HZ_OFFER_CACHE_TTL_MS) {
                return cached.value;
            }
            List<NameCount> result = queryHzOfferDistribution(null);
            if (result.isEmpty()) {
                String anchor = resolveHzLatestDate();
                if (anchor != null) {
                    result = queryHzOfferDistribution(anchor);
                }
            }
            hzOfferCache.put("all", new CachedNameCounts(System.currentTimeMillis(), result));
            return result;
        }
    }

    private List<NameCount> queryHzOfferDistribution(String anchorDate) {
        String anchorExpr = anchorDate == null ? "CURRENT_DATE" : "DATE('" + anchorDate + "')";

        String sql = "SELECT APN AS name, COUNT(*) AS count FROM hz "
                + "WHERE APN IS NOT NULL AND APN <> '' "
                + "AND `Time` >= DATE_FORMAT(" + anchorExpr + " - INTERVAL " + (HZ_DAILY_DAYS - 1) + " DAY, '%Y-%m-%d 00:00:00') "
                + "AND `Time` < DATE_FORMAT(" + anchorExpr + " + INTERVAL 1 DAY, '%Y-%m-%d 00:00:00') "
                + "GROUP BY APN ORDER BY count DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<NameCount> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = row.get("name").toString();
            long count = ((Number) row.get("count")).longValue();
            result.add(new NameCount(name, count));
        }
        return result;
    }

    private String resolveHzLatestDate() {
        long now = System.currentTimeMillis();
        if (hzLatestDateCache != null && (now - hzLatestDateCacheTs) < HZ_LATEST_CACHE_TTL_MS) {
            return hzLatestDateCache;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (hzLatestDateCache != null && (now - hzLatestDateCacheTs) < HZ_LATEST_CACHE_TTL_MS) {
                return hzLatestDateCache;
            }
            List<String> dates = jdbcTemplate.queryForList(
                    "SELECT DATE_FORMAT(MAX(`Time`), '%Y-%m-%d') AS latest FROM hz", String.class);
            String latest = dates.isEmpty() || dates.get(0) == null ? null : dates.get(0);
            hzLatestDateCache = latest;
            hzLatestDateCacheTs = now;
            return latest;
        }
    }

    public List<HzError> getHzErrors(String date, String status, String apn, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT MAX(`Time`) AS time, MSISDN, MAX(IMSI) AS imsi, MAX(site_Name) AS siteName, "
                        + "MAX(error_code) AS errorCode, status, MAX(APN) AS apn, COUNT(*) AS count "
                        + "FROM hz WHERE `Time` >= ? AND `Time` < ? AND status = ? ");
        List<Object> args = new ArrayList<>();
        args.add(date + " 00:00:00");
        args.add(date + " 23:59:59");
        args.add(status);

        if (apn != null && !apn.trim().isEmpty() && !apn.trim().equalsIgnoreCase("all")) {
            sql.append(" AND APN = ? ");
            args.add(apn.trim());
        }

        sql.append(" GROUP BY MSISDN ORDER BY count DESC, MSISDN LIMIT ").append(Math.min(Math.max(limit, 1), 1000));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<HzError> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new HzError(
                    row.get("time") == null ? null : row.get("time").toString(),
                    row.get("msisdn") == null ? null : ((Number) row.get("msisdn")).longValue(),
                    row.get("imsi") == null ? null : ((Number) row.get("imsi")).longValue(),
                    row.get("siteName") == null ? null : row.get("siteName").toString(),
                    row.get("errorCode") == null ? null : ((Number) row.get("errorCode")).longValue(),
                    row.get("status") == null ? null : row.get("status").toString(),
                    row.get("apn") == null ? null : row.get("apn").toString(),
                    ((Number) row.get("count")).longValue(),
                    null, null, null, null, null, null, false, false, null, null
            ));
        }
        return result;
    }

    public HzMsisdnStats getHzMsisdnStats(Long msisdn, String dateFrom, String dateTo) {
        Long fullMsisdn = HZ_MSISDN_OFFSET + msisdn;

        StringBuilder statusSql = new StringBuilder(
                "SELECT status, COUNT(*) AS cnt FROM hz WHERE MSISDN = ? AND status IS NOT NULL AND status <> '' ");
        List<Object> args = new ArrayList<>();
        args.add(fullMsisdn);
        appendDateRange(statusSql, args, dateFrom, dateTo, "`Time`");
        statusSql.append(" GROUP BY status ORDER BY cnt DESC");

        List<Map<String, Object>> statusRows = jdbcTemplate.queryForList(statusSql.toString(), args.toArray());

        long total = 0L;
        List<NameCount> byStatus = new ArrayList<>();
        for (Map<String, Object> row : statusRows) {
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            byStatus.add(new NameCount(row.get("status").toString(), cnt));
        }

        List<HzError> recent = new ArrayList<>();
        if (total > 0) {
            StringBuilder recentSql = new StringBuilder(
                    "SELECT `Time` AS time, MSISDN, IMSI, site_Name AS siteName, error_code AS errorCode, " +
                    "status, APN FROM hz WHERE MSISDN = ? ");
            List<Object> recentArgs = new ArrayList<>();
            recentArgs.add(fullMsisdn);
            appendDateRange(recentSql, recentArgs, dateFrom, dateTo, "`Time`");
            recentSql.append(" ORDER BY `Time` DESC LIMIT 200");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(recentSql.toString(), recentArgs.toArray());
            for (Map<String, Object> row : rows) {
                recent.add(new HzError(
                        row.get("time") == null ? null : row.get("time").toString(),
                        row.get("msisdn") == null ? null : ((Number) row.get("msisdn")).longValue(),
                        row.get("imsi") == null ? null : ((Number) row.get("imsi")).longValue(),
                        row.get("siteName") == null ? null : row.get("siteName").toString(),
                        row.get("errorCode") == null ? null : ((Number) row.get("errorCode")).longValue(),
                        row.get("status") == null ? null : row.get("status").toString(),
                        row.get("apn") == null ? null : row.get("apn").toString(),
                        1L,
                        null, null, null, null, null, null, false, false, null, null
                ));
            }
            enrichErrorsWithDeviceInfo(recent);
        }

        return new HzMsisdnStats(msisdn, total, byStatus, recent);
    }

    private void enrichErrorsWithDeviceInfo(List<HzError> errors) {
        List<Long> originalMsisdns = errors.stream()
                .filter(e -> e.getMsisdn() != null)
                .map(e -> e.getMsisdn() - HZ_MSISDN_OFFSET)
                .distinct()
                .collect(Collectors.toList());
        if (originalMsisdns.isEmpty()) {
            return;
        }

        Map<Long, String> imsiByOriginalMsisdn = new HashMap<>();
        List<String> imsis = new ArrayList<>();
        for (Long m : originalMsisdns) {
            Long fullMsisdn = HZ_MSISDN_OFFSET + m;
            Optional<Long> imsiOpt = fixboxRepository.findImsiByMsisdn(fullMsisdn);
            if (imsiOpt.isPresent()) {
                String imsi = imsiOpt.get().toString();
                imsiByOriginalMsisdn.put(m, imsi);
                imsis.add(imsi);
            }
        }
        if (imsis.isEmpty()) {
            return;
        }

        Map<String, List<AcsMaxBox5G>> devicesByImsi = acsRepository.findAllByImsiIn(imsis).stream()
                .filter(d -> d.getImsi() != null)
                .collect(Collectors.groupingBy(d -> d.getImsi().replace("\r", "").trim()));

        loadCaches();
        for (HzError e : errors) {
            if (e.getMsisdn() == null) continue;
            Long originalMsisdn = e.getMsisdn() - HZ_MSISDN_OFFSET;
            String imsi = imsiByOriginalMsisdn.get(originalMsisdn);
            if (imsi == null) continue;
            List<AcsMaxBox5G> devices = devicesByImsi.get(imsi);
            if (devices == null || devices.isEmpty()) continue;
            AcsMaxBox5G device = devices.get(0);
            e.setRsrp4G(device.getRsrp());
            e.setSinr4G(device.getSinr());
            e.setRsrp5G(device.getRsrp5G());
            e.setSinr5G(device.getSinr5G());

            IncidentWithDeviceInfo tmp = new IncidentWithDeviceInfo();
            resolveCellInfoCached(device, tmp);
            e.setCellName(tmp.getCellName());
            e.setCellName5G(tmp.getCellName5G());
            e.setCongestionnee(tmp.isCongestionnee());
            e.setSiteCode(tmp.getSiteCode());
            resolveIncidentForSite(tmp, e.getTime());
            e.setHasIncident(tmp.isHasIncident());
            e.setIncidentPeriod(tmp.getIncidentPeriod());
        }
    }

    private static void appendDateRange(StringBuilder sql, List<Object> args, String dateFrom, String dateTo, String col) {
        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            sql.append(" AND ").append(col).append(" >= ? ");
            args.add(dateFrom.trim() + " 00:00:00");
        }
        if (dateTo != null && !dateTo.trim().isEmpty()) {
            sql.append(" AND ").append(col).append(" <= ? ");
            args.add(dateTo.trim() + " 23:59:59");
        }
    }

    public List<HzDailySeries> getHzMsisdnDailyEvolution(Long msisdn, String dateFrom, String dateTo) {
        if (msisdn == null) {
            return Collections.emptyList();
        }
        Long fullMsisdn = HZ_MSISDN_OFFSET + msisdn;

        StringBuilder sql = new StringBuilder(
                "SELECT DATE(`Time`) AS day, status, COUNT(*) AS devices FROM hz " +
                "WHERE MSISDN = ? AND status IS NOT NULL AND status <> '' ");
        List<Object> args = new ArrayList<>();
        args.add(fullMsisdn);
        appendDateRange(sql, args, dateFrom, dateTo, "`Time`");
        sql.append(" GROUP BY DATE(`Time`), status ORDER BY status, day");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());

        Map<String, List<HzDailyPoint>> seriesByStatus = new LinkedHashMap<>();
        List<String> statusOrder = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String status = row.get("status").toString();
            String date = row.get("day").toString();
            long devices = ((Number) row.get("devices")).longValue();
            if (!seriesByStatus.containsKey(status)) {
                seriesByStatus.put(status, new ArrayList<>());
                statusOrder.add(status);
            }
            seriesByStatus.get(status).add(new HzDailyPoint(date, devices));
        }

        List<HzDailySeries> result = new ArrayList<>();
        for (String status : statusOrder) {
            result.add(new HzDailySeries(status, seriesByStatus.get(status)));
        }
        return result;
    }

    public TopZonesResponse getTopZones(int limit) {
        List<ReUn22906> incidents = getFilteredIncidents();
        Map<Long, List<AcsMaxBox5G>> devicesByMsisdn = buildDevicesByMsisdn(incidents);
        loadCaches();

        Map<String, Long> counts = new HashMap<>();
        for (ReUn22906 inc : incidents) {
            if (inc.getMsisdn() == null) continue;
            List<AcsMaxBox5G> devices = devicesByMsisdn.get(inc.getMsisdn());
            if (devices == null || devices.isEmpty()) continue;
            String site = resolveSitePrefix(devices.get(0));
            if (site != null) {
                counts.merge(site, 1L, Long::sum);
            }
        }

        List<NameCount> zones = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return new TopZonesResponse(zones, (long) counts.size());
    }

    public List<NearbySite> getNearbySites(Double lat, Double lng, double radiusMeters, LocalDate targetDate) {
        loadCaches();
        List<NearbySite> result = new ArrayList<>();
        if (lat == null || lng == null || siteCache == null) {
            return result;
        }
        for (Map.Entry<String, Double[]> e : siteCache.entrySet()) {
            String site = e.getKey();
            Double[] coords = e.getValue();
            double dist = haversine(lat, lng, coords[0], coords[1]);
            if (dist <= radiusMeters) {
                NearbySite ns = new NearbySite();
                ns.setSite(site);
                ns.setLatitude(coords[0]);
                ns.setLongitude(coords[1]);
                List<IncidentPeriod> periods = incidentSiteCache != null ? incidentSiteCache.get(site) : null;
                if (periods != null && !periods.isEmpty()) {
                    List<IncidentPeriod> matching = periods;
                    if (targetDate != null) {
                        LocalDate from = targetDate.minusDays(1);
                        LocalDate to = targetDate.plusDays(1);
                        matching = periods.stream()
                                .filter(p -> p.debut != null
                                        && !p.debut.toLocalDate().isBefore(from)
                                        && !p.debut.toLocalDate().isAfter(to))
                                .collect(Collectors.toList());
                    }
                    if (!matching.isEmpty()) {
                        IncidentPeriod last = matching.get(matching.size() - 1);
                        String debut = last.debut != null ? last.debut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                        String fin = last.fin != null ? last.fin.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                        ns.setHasIncident(true);
                        ns.setIncidentPeriod(debut + " \u2192 " + fin);
                        ns.setIncidentTech(resolveTechLabel(last.services));
                    }
                }
                result.add(ns);
            }
        }
        result.sort(Comparator.comparingDouble(ns -> haversine(lat, lng, ns.getLatitude(), ns.getLongitude())));
        return result;
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String resolveSitePrefix(AcsMaxBox5G device) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return null;
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            String rawCellName = lteCellCache != null ? lteCellCache.get(eNodeBId + "_" + localCellIdentity) : null;
            if (rawCellName != null && rawCellName.length() >= 8) {
                String sitePrefix = rawCellName.substring(0, 8);
                if (siteCache != null && siteCache.containsKey(sitePrefix)) {
                    return sitePrefix;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Map<Long, String> buildHzErrors(List<ReUn22906> incidents) {
        Map<Long, String> result = new HashMap<>();
        if (incidents.isEmpty()) {
            return result;
        }

        Map<Long, Map<String, Long>> countsByMsisdn = new HashMap<>();
        for (Map<String, Object> row : fetchHzCounts()) {
            long incMsisdn = ((Number) row.get("inc_msisdn")).longValue();
            countsByMsisdn.computeIfAbsent(incMsisdn, k -> new HashMap<>())
                    .merge(row.get("status").toString(), ((Number) row.get("cnt")).longValue(), Long::sum);
        }

        for (Map.Entry<Long, Map<String, Long>> e : countsByMsisdn.entrySet()) {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(e.getValue().entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            List<String> errors = new ArrayList<>();
            for (Map.Entry<String, Long> p : entries) {
                errors.add(p.getKey() + " (" + p.getValue() + ")");
            }
            result.put(e.getKey(), String.join(", ", errors));
        }
        return result;
    }

    private List<Map<String, Object>> fetchHzCounts() {
        String sql = "SELECT STRAIGHT_JOIN h.MSISDN - " + HZ_MSISDN_OFFSET + " AS inc_msisdn, h.status, COUNT(*) AS cnt "
                + "FROM re_u_n2_29_06 r "
                + "JOIN hz h ON h.MSISDN = " + HZ_MSISDN_OFFSET + " + CAST(r.MSISDN_concern\u00e9 AS UNSIGNED) "
                + "WHERE h.status IS NOT NULL AND h.status <> '' "
                + "AND h.Time >= DATE_SUB(r.Cr\u00e9\u00e9_le, INTERVAL " + HZ_WINDOW_DAYS + " DAY) "
                + "AND h.Time <= DATE_ADD(r.Cr\u00e9\u00e9_le, INTERVAL " + HZ_WINDOW_DAYS + " DAY) "
                + "GROUP BY h.MSISDN, h.status";
        return jdbcTemplate.queryForList(sql);
    }

    private void resolveCellInfoCached(AcsMaxBox5G device, IncidentWithDeviceInfo info) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return;
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            String rawCellName = null;
            if (lteCellCache != null) {
                rawCellName = lteCellCache.get(eNodeBId + "_" + localCellIdentity);
            }
            if (rawCellName != null) {
                String fullCellName = eNodeBId + "" + localCellIdentity + "" + rawCellName;
                info.setCellName(fullCellName);
                if (rawCellName.length() >= 8) {
                    String sitePrefix = rawCellName.substring(0, 8);
                    info.setSiteCode(sitePrefix);
                    if (siteCache != null) {
                        Double[] coords = siteCache.get(sitePrefix);
                        if (coords != null) {
                            info.setLatitude(coords[0]);
                            info.setLongitude(coords[1]);
                        }
                    }
                }
                if (rawCellName.length() >= 3) {
                    String prefix = rawCellName.substring(0, 3).toUpperCase();
                    Double pci5G = device.getPci5G();
                    if (pci5G != null) {
                        String key = prefix + pci5G.intValue();
                        String cellName5G = nrCellCache != null ? nrCellCache.get(key) : null;
                        if (cellName5G != null) {
                            info.setCellName5G(cellName5G);
                        }
                    }
                }
            } else {
                info.setCellName(cellId);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void resolveIncidentForSite(IncidentWithDeviceInfo info, String reclamationCreated) {
        info.setHasIncident(false);
        String siteCode = info.getSiteCode();
        if (siteCode == null || incidentSiteCache == null) {
            return;
        }
        LocalDateTime reclamationDt = parseReclamationDate(reclamationCreated);
        if (reclamationDt == null) {
            return;
        }
        List<IncidentPeriod> periods = incidentSiteCache.get(siteCode);
        if (periods == null || periods.isEmpty()) {
            return;
        }
        for (IncidentPeriod p : periods) {
            if (p.debut == null) {
                continue;
            }
            if (p.debut.toLocalDate().equals(reclamationDt.toLocalDate()) && !p.debut.isAfter(reclamationDt)) {
                info.setHasIncident(true);
                String debut = p.debut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                String fin = p.fin != null ? p.fin.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "-";
                info.setIncidentPeriod(debut + " \u2192 " + fin);
                info.setIncidentTech(resolveTechLabel(p.services));
                return;
            }
        }
    }

    private static String resolveTechLabel(String services) {
        if (services == null || services.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> techs = new LinkedHashSet<>();
        for (String part : services.trim().split("/")) {
            String t = part.trim();
            if (t.equals("4G") || t.equals("5G") || t.equals("4G_TDD") || t.equals("TDD")) {
                techs.add(t.equals("4G_TDD") || t.equals("TDD") ? "4G" : t);
            }
        }
        return techs.isEmpty() ? services.trim() : String.join("/", techs);
    }

    private static LocalDateTime parseReclamationDate(String created) {
        if (created == null || created.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
