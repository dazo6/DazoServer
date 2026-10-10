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
        ArtistUpdate artistUpdate = new ArtistUpdate("Artist (123)", "2026-09-12 12:30");
        artistUpdate.setIcon("artist-7-icon-0123456789abcdef0123456789abcdef.jpg");
        artistUpdate.setBanner("https://external.example/banner.jpg");
        when(mapper.selectArtistUpdates()).thenReturn(Collections.singletonList(artistUpdate));
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
        assertThat(data.getArtistUpdates().get(0).getIcon()).isEqualTo(
                "/api/fanbox/artist-image/artist-7-icon-0123456789abcdef0123456789abcdef.jpg");
        assertThat(data.getArtistUpdates().get(0).getBanner()).isNull();
    }

    @Test
    void hourlyDetailsIncludeUnknownAuthorsAndUseAnExpiringCache() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        java.util.concurrent.atomic.AtomicReference<Instant> time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-09-12T04:34:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
            public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
            public Instant instant() { return time.get(); }
        };
        when(mapper.selectHourlyArtists("2026-09-12 12:00:00", "2026-09-12 13:00:00")).thenReturn(Arrays.asList(
                new com.dazo66.dashboard.HourlyArtistCount(7, "Small", "https://outside.example/icon.jpg", 2),
                new com.dazo66.dashboard.HourlyArtistCount(null, null, null, 3),
                new com.dazo66.dashboard.HourlyArtistCount(8, "Large", "artist-8-icon-0123456789abcdef0123456789abcdef.jpg", 5)));
        DashboardServiceImpl service = new DashboardServiceImpl(mapper, clock);
        com.dazo66.dashboard.HourlyArtistDetails details = service.getHourlyArtists("2026-09-12 12:00");
        assertThat(details.getTotal()).isEqualTo(10);
        assertThat(details.getUnknownCount()).isEqualTo(3);
        assertThat(details.getArtists()).extracting(com.dazo66.dashboard.HourlyArtistCount::getArtistId).containsExactly(8, 7);
        assertThat(details.getArtists().get(0).getIcon()).startsWith("/api/fanbox/artist-image/");
        assertThat(details.getArtists().get(1).getIcon()).isNull();
        time.set(time.get().plusSeconds(59));
        assertThat(service.getHourlyArtists("2026-09-12 12:00")).isSameAs(details);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(1)).selectHourlyArtists(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        time.set(time.get().plusSeconds(1));
        assertThat(service.getHourlyArtists("2026-09-12 12:00")).isNotSameAs(details);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(2)).selectHourlyArtists(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsInvalidOrOutOfWindowHoursWithoutQuerying() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T04:34:00Z"), ZoneId.of("Asia/Shanghai"));
        DashboardServiceImpl service = new DashboardServiceImpl(mapper, clock);
        for (String hour : new String[]{null, "2026-02-30 12:00", "2026-09-12 12:01", "2026-09-12 13:00", "2026-09-06 06:00", "2026-09-12 12:00:00", "invalid"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getHourlyArtists(hour))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(mapper);
        assertThat(service.getHourlyArtists("2026-09-06 07:00").getTotal()).isZero();
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
