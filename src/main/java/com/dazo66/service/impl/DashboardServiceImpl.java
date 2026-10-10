package com.dazo66.service.impl;

import com.dazo66.dashboard.ArtistUpdate;
import com.dazo66.dashboard.HourlyArtistCount;
import com.dazo66.dashboard.HourlyArtistDetails;
import java.time.format.ResolverStyle;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.dazo66.dashboard.DashboardData;
import com.dazo66.dashboard.HourlyCount;
import com.dazo66.dashboard.StatusCount;
import com.dazo66.mapper.DashboardMapper;
import com.dazo66.service.ArtistImageCache;
import com.dazo66.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardServiceImpl implements DashboardService {

    static final int HOURS_SHOWN = 150;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    private static final DateTimeFormatter INPUT_HOUR = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DETAIL_CACHE_MS = 60_000;
    private final Map<String, CachedDetails> detailCache = new HashMap<>();

    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    @Autowired
    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this(dashboardMapper, Clock.systemDefaultZone());
    }

    DashboardServiceImpl(DashboardMapper dashboardMapper, Clock clock) {
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    @Override
    public DashboardData getDashboardData() {
        List<HourlyCount> hourlyCounts = fillHourlyGaps(dashboardMapper.selectHourlyCounts());
        List<StatusCount> statusCounts = includeAllStatuses(dashboardMapper.selectStatusCounts());
        List<ArtistUpdate> artistUpdates = dashboardMapper.selectArtistUpdates();
        if (artistUpdates == null) {
            artistUpdates = Collections.emptyList();
        } else {
            for (ArtistUpdate artist : artistUpdates) {
                artist.setIcon(ArtistImageCache.publicUrl(artist.getIcon()));
                artist.setBanner(ArtistImageCache.publicUrl(artist.getBanner()));
            }
        }
        return new DashboardData(hourlyCounts, statusCounts, artistUpdates);
    }

    @Override
    public HourlyArtistDetails getHourlyArtists(String hour) {
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(hour, INPUT_HOUR);
            if (start.getMinute() != 0 || !start.format(HOUR_FORMAT).equals(hour)) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hour; expected yyyy-MM-dd HH:00");
        }
        LocalDateTime current = LocalDateTime.now(clock).withMinute(0).withSecond(0).withNano(0);
        if (start.isBefore(current.minusHours(HOURS_SHOWN - 1L)) || start.isAfter(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hour is outside the dashboard timeline");
        }
        synchronized (detailCache) {
            long now = clock.millis();
            detailCache.entrySet().removeIf(entry -> now - entry.getValue().loadedAt >= DETAIL_CACHE_MS);
            CachedDetails cached = detailCache.get(hour);
            if (cached != null) return cached.details;
            List<HourlyArtistCount> rows = dashboardMapper.selectHourlyArtists(start.format(SQL_TIME), start.plusHours(1).format(SQL_TIME));
            List<HourlyArtistCount> artists = new ArrayList<>();
            long total = 0, unknown = 0;
            if (rows != null) for (HourlyArtistCount row : rows) {
                total += row.getCount();
                if (row.getArtistId() == null) unknown += row.getCount();
                else artists.add(new HourlyArtistCount(row.getArtistId(), row.getName(),
                        ArtistImageCache.publicUrl(row.getIcon()), row.getCount()));
            }
            artists.sort(Comparator.comparingLong(HourlyArtistCount::getCount).reversed()
                    .thenComparing(HourlyArtistCount::getArtistId));
            HourlyArtistDetails details = new HourlyArtistDetails(hour, total, unknown, Collections.unmodifiableList(artists));
            detailCache.put(hour, new CachedDetails(clock.millis(), details));
            return details;
        }
    }

    private static final class CachedDetails {
        final long loadedAt;
        final HourlyArtistDetails details;
        CachedDetails(long loadedAt, HourlyArtistDetails details) { this.loadedAt = loadedAt; this.details = details; }
    }

    private List<HourlyCount> fillHourlyGaps(List<HourlyCount> rows) {
        Map<String, Long> countsByHour = new HashMap<>();
        if (rows != null) {
            for (HourlyCount row : rows) {
                countsByHour.put(row.getHour(), row.getCount());
            }
        }

        LocalDateTime lastHour = LocalDateTime.now(clock).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime firstHour = lastHour.minusHours(HOURS_SHOWN - 1L);
        List<HourlyCount> result = new ArrayList<>(HOURS_SHOWN);
        for (int offset = 0; offset < HOURS_SHOWN; offset++) {
            String hour = firstHour.plusHours(offset).format(HOUR_FORMAT);
            result.add(new HourlyCount(hour, countsByHour.getOrDefault(hour, 0L)));
        }
        return result;
    }

    private List<StatusCount> includeAllStatuses(List<StatusCount> rows) {
        long pending = 0;
        long completed = 0;
        if (rows != null) {
            for (StatusCount row : rows) {
                if (row.isDone()) {
                    completed += row.getCount();
                } else {
                    pending += row.getCount();
                }
            }
        }
        List<StatusCount> result = new ArrayList<>(2);
        result.add(new StatusCount(false, pending));
        result.add(new StatusCount(true, completed));
        return result;
    }
}
