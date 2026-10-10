package com.dazo66.service;

import com.dazo66.dashboard.DashboardData;

public interface DashboardService {

    DashboardData getDashboardData();

    com.dazo66.dashboard.HourlyArtistDetails getHourlyArtists(String hour);
}
