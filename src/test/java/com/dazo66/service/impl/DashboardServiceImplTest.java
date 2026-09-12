package com.dazo66.service.impl;

import com.dazo66.dashboard.ArtistUpdate;
import com.dazo66.dashboard.DashboardData;
import com.dazo66.dashboard.HourlyCount;
import com.dazo66.dashboard.StatusCount;
import com.dazo66.mapper.DashboardMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Test
    void springUsesTheDashboardMapperConstructor() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DashboardMapper.class, () -> mapper);
        context.register(DashboardServiceImpl.class);
        context.refresh();

        assertThat(context.getBean(DashboardServiceImpl.class)).isNotNull();
        context.close();
    }

    @Test
    void buildsAComplete150HourTimelineAndBothStatuses() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        when(mapper.selectHourlyCounts()).thenReturn(Arrays.asList(
                new HourlyCount("2026-09-12 10:00", 3),
                new HourlyCount("2026-09-12 12:00", 7)
        ));
        when(mapper.selectStatusCounts()).thenReturn(Collections.singletonList(new StatusCount(true, 10)));
        when(mapper.selectArtistUpdates()).thenReturn(Collections.singletonList(
                new ArtistUpdate("Artist (123)", "2026-09-12 12:30")
        ));
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T04:34:00Z"), ZoneId.of("Asia/Shanghai"));

        DashboardData data = new DashboardServiceImpl(mapper, clock).getDashboardData();

        assertThat(data.getHourlyCounts()).hasSize(DashboardServiceImpl.HOURS_SHOWN);
        assertThat(data.getHourlyCounts().get(0).getHour()).isEqualTo("2026-09-06 07:00");
        assertThat(data.getHourlyCounts().get(147).getCount()).isEqualTo(3);
        assertThat(data.getHourlyCounts().get(149).getCount()).isEqualTo(7);
        assertThat(data.getStatusCounts())
                .extracting(StatusCount::isDone, StatusCount::getCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(false, 0L),
                        org.assertj.core.groups.Tuple.tuple(true, 10L)
                );
        assertThat(data.getArtistUpdates()).hasSize(1);
    }

    @Test
    void toleratesEmptyMapperResults() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        when(mapper.selectHourlyCounts()).thenReturn(null);
        when(mapper.selectStatusCounts()).thenReturn(null);
        when(mapper.selectArtistUpdates()).thenReturn(null);
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

        DashboardData data = new DashboardServiceImpl(mapper, clock).getDashboardData();

        assertThat(data.getHourlyCounts()).hasSize(150).allMatch(row -> row.getCount() == 0);
        assertThat(data.getStatusCounts()).hasSize(2).allMatch(row -> row.getCount() == 0);
        assertThat(data.getArtistUpdates()).isEmpty();
    }
}
