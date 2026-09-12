(() => {
    "use strict";

    const {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} = Vue;
    const numberFormat = new Intl.NumberFormat("zh-CN");
    window.DazoAdminPages = window.DazoAdminPages || {};

    window.DazoAdminPages.DashboardHome = {
        template: "#dashboard-home-template",
        setup() {
            const hourlyCounts = ref([]);
            const statusCounts = ref([]);
            const artists = ref([]);
            const loading = ref(false);
            const refreshing = ref(false);
            const errorMessage = ref("");
            const lastUpdated = ref("");
            const searchKeyword = ref("");
            const currentPage = ref(1);
            const pageSize = 10;
            const timelineChart = ref(null);
            const statusChart = ref(null);
            let refreshTimer;
            let timelineInstance;
            let statusInstance;
            let hasLoaded = false;

            const timelineTotal = computed(() => hourlyCounts.value.reduce(
                (sum, row) => sum + Number(row.count || 0), 0
            ));
            const formattedTimelineTotal = computed(() => hourlyCounts.value.length
                ? numberFormat.format(timelineTotal.value)
                : "—");
            const completed = computed(() => Number(
                (statusCounts.value.find((row) => row.done) || {}).count || 0
            ));
            const pending = computed(() => Number(
                (statusCounts.value.find((row) => !row.done) || {}).count || 0
            ));
            const statusTotal = computed(() => completed.value + pending.value);
            const completedPercent = computed(() => statusTotal.value
                ? completed.value / statusTotal.value * 100
                : 0);
            const statusAriaLabel = computed(() =>
                `已完成 ${completed.value}，待处理 ${pending.value}，完成率 ${completedPercent.value.toFixed(1)}%`
            );
            const filteredArtists = computed(() => {
                const keyword = searchKeyword.value.toLocaleLowerCase();
                return keyword
                    ? artists.value.filter((artist) => (artist.name || "").toLocaleLowerCase().includes(keyword))
                    : artists.value;
            });
            const filteredArtistCount = computed(() => filteredArtists.value.length);
            const pageCount = computed(() => Math.max(1, Math.ceil(filteredArtists.value.length / pageSize)));
            const pagedArtists = computed(() => {
                const start = (currentPage.value - 1) * pageSize;
                return filteredArtists.value.slice(start, start + pageSize);
            });
            const artistInfo = computed(() => {
                const total = filteredArtists.value.length;
                if (!total) return "共 0 条";
                const start = (currentPage.value - 1) * pageSize;
                return `显示 ${start + 1}–${Math.min(start + pageSize, total)}，共 ${total} 条`;
            });

            watch(searchKeyword, () => {
                currentPage.value = 1;
            });
            watch(pageCount, (totalPages) => {
                currentPage.value = Math.min(currentPage.value, totalPages);
            });

            async function loadDashboard() {
                if (loading.value || refreshing.value) return;
                const initialLoad = !hasLoaded;
                if (initialLoad) {
                    loading.value = true;
                } else {
                    // Keep the existing cards visible while the minute refresh runs.
                    refreshing.value = true;
                }
                try {
                    const result = await requestJson("/api/dashboard");
                    hourlyCounts.value = result.data.hourlyCounts || [];
                    statusCounts.value = result.data.statusCounts || [];
                    artists.value = result.data.artistUpdates || [];
                    if (initialLoad) {
                        currentPage.value = 1;
                    }
                    hasLoaded = true;
                    lastUpdated.value = formatRefreshTime(new Date());
                    errorMessage.value = "";
                    await nextTick();
                    renderCharts();
                } catch (error) {
                    errorMessage.value = `仪表盘数据加载失败：${error.message}`;
                } finally {
                    loading.value = false;
                    refreshing.value = false;
                }
            }

            function renderCharts() {
                if (timelineChart.value) {
                    timelineInstance = timelineInstance || echarts.init(timelineChart.value);
                    timelineInstance.setOption(timelineOption(hourlyCounts.value), true);
                }
                if (statusChart.value) {
                    statusInstance = statusInstance || echarts.init(statusChart.value);
                    statusInstance.setOption(statusOption(completed.value, pending.value), true);
                }
            }

            function resizeCharts() {
                if (timelineInstance) timelineInstance.resize();
                if (statusInstance) statusInstance.resize();
            }

            function hideBrokenArtistImage(artist, field) {
                if (artist && (field === "icon" || field === "banner")) {
                    artist[field] = "";
                }
            }

            onMounted(() => {
                loadDashboard();
                refreshTimer = window.setInterval(loadDashboard, 60_000);
                window.addEventListener("resize", resizeCharts);
            });

            onBeforeUnmount(() => {
                window.clearInterval(refreshTimer);
                window.removeEventListener("resize", resizeCharts);
                if (timelineInstance) timelineInstance.dispose();
                if (statusInstance) statusInstance.dispose();
            });

            return {
                artistInfo,
                artists,
                currentPage,
                errorMessage,
                filteredArtistCount,
                formattedTimelineTotal,
                hideBrokenArtistImage,
                hourlyCounts,
                loading,
                refreshing,
                lastUpdated,
                pageSize,
                pagedArtists,
                searchKeyword,
                statusAriaLabel,
                statusChart,
                statusTotal,
                timelineChart
            };
        }
    };

    async function requestJson(url) {
        const response = await fetch(url, {
            cache: "no-store",
            headers: {Accept: "application/json", "Cache-Control": "no-cache"}
        });
        redirectWhenUnauthorized(response);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const result = await response.json();
        if (result.result !== "SUCCESS" || !result.data) {
            throw new Error(result.message || "接口返回异常");
        }
        return result;
    }

    function formatRefreshTime(value) {
        return value.toLocaleTimeString("zh-CN", {hour: "2-digit", minute: "2-digit", second: "2-digit"});
    }

    function redirectWhenUnauthorized(response) {
        if (response.status === 401) {
            const target = location.pathname + location.search + location.hash;
            location.replace(`/login.html?redirect=${encodeURIComponent(target)}`);
        }
    }

    function timelineOption(rows) {
        return {
            animationDuration: 500,
            color: ["#218ec5"],
            tooltip: {
                trigger: "axis",
                valueFormatter: (value) => `${numberFormat.format(value)} 次`
            },
            grid: {top: 22, right: 20, bottom: 45, left: 52},
            xAxis: {
                type: "category",
                boundaryGap: false,
                data: rows.map((row) => row.hour),
                axisLine: {lineStyle: {color: "#d9e1e9"}},
                axisTick: {show: false},
                axisLabel: {
                    color: "#738094",
                    hideOverlap: true,
                    formatter: (value) => value ? value.slice(5) : ""
                }
            },
            yAxis: {
                type: "value",
                minInterval: 1,
                axisLabel: {color: "#738094"},
                splitLine: {lineStyle: {color: "#e8edf3"}}
            },
            series: [{
                name: "count",
                type: "line",
                data: rows.map((row) => Number(row.count || 0)),
                showSymbol: false,
                smooth: 0.16,
                lineStyle: {width: 2},
                areaStyle: {
                    opacity: 1,
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        {offset: 0, color: "rgba(33, 142, 197, .45)"},
                        {offset: 1, color: "rgba(85, 191, 218, .05)"}
                    ])
                },
                emphasis: {focus: "series"}
            }]
        };
    }

    function statusOption(completed, pending) {
        const total = completed + pending;
        const percent = total ? completed / total * 100 : 0;
        const counts = {"已完成": completed, "待处理": pending};
        return {
            animationDuration: 500,
            color: ["#218ec5", "#55bfda"],
            tooltip: {
                trigger: "item",
                formatter: (params) => `${params.marker}${params.name}<br>${numberFormat.format(params.value)} · ${params.percent}%`
            },
            title: {
                text: `${percent.toFixed(1)}%`,
                subtext: "完成率",
                left: "center",
                top: "38%",
                textStyle: {color: "#172033", fontSize: 28, fontWeight: 700},
                subtextStyle: {color: "#687386", fontSize: 12}
            },
            legend: {
                bottom: 8,
                icon: "roundRect",
                itemWidth: 10,
                itemHeight: 10,
                formatter: (name) => `${name}  ${numberFormat.format(counts[name])}`
            },
            series: [{
                name: "请求状态",
                type: "pie",
                radius: ["52%", "72%"],
                center: ["50%", "43%"],
                label: {show: false},
                emphasis: {scaleSize: 8},
                data: [
                    {name: "已完成", value: completed},
                    {name: "待处理", value: pending}
                ]
            }]
        };
    }
})();
