package com.dazo66.service.impl;

import com.dazo66.dashboard.ArtistUpdate;
import com.dazo66.dashboard.DashboardData;
import com.dazo66.dashboard.HourlyCount;
import com.dazo66.dashboard.StatusCount;
import com.dazo66.mapper.DashboardMapper;
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
        }
        return new DashboardData(hourlyCounts, statusCounts, artistUpdates);
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
