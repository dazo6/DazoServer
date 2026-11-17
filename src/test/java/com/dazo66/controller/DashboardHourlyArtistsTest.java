package com.dazo66.controller;
import com.dazo66.dashboard.DashboardData;
import com.dazo66.dashboard.HourlyCount;
import com.dazo66.dashboard.HourlyArtistCount;
import com.dazo66.dashboard.HourlyArtistDetails;
import com.dazo66.security.CurrentUser;
import com.dazo66.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardHourlyArtistsTest {
    @AfterEach void logout() { CurrentUser.clear(); }
    @Test void returnsDetailsToLoggedInUsersWithoutPermissions() throws Exception {
        DashboardService service = mock(DashboardService.class);
        when(service.getHourlyArtists("2026-09-12 12:00")).thenReturn(new HourlyArtistDetails("2026-09-12 12:00", 5, 2,
                Collections.singletonList(new HourlyArtistCount(7, "Artist", null, 3))));
        CurrentUser.set(new CurrentUser("reader", "reader", false, Collections.emptyList(), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
        mvc.perform(get("/api/dashboard/hour-artists").param("hour", "2026-09-12 12:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.unknownCount").value(2))
                .andExpect(jsonPath("$.data.artists[0].artistId").value(7))
                .andExpect(jsonPath("$.data.artists[0].name").value("Artist"));
    }
    @Test void deniesAnonymousUsersBeforeQuerying() throws Exception {
        DashboardService service = mock(DashboardService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
        mvc.perform(get("/api/dashboard/hour-artists").param("hour", "2026-09-12 12:00")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void returnsDashboardDataToLoggedInUsersWithoutPermissions() throws Exception {
        DashboardService service = mock(DashboardService.class);
        DashboardData data = new DashboardData(Collections.singletonList(new HourlyCount()), Collections.emptyList(), Collections.emptyList());
        when(service.getDashboardData()).thenReturn(data);
        CurrentUser.set(new CurrentUser("reader", "reader", false, Collections.emptyList(), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hourlyCounts.length()").value(1));
        verify(service).getDashboardData();
    }
}
