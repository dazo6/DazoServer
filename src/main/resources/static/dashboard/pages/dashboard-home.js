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
            const timelineTooltip = createTimelineTooltip(requestJson);

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
                    timelineInstance.setOption(timelineOption(hourlyCounts.value, timelineTooltip.formatter), true);
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
                timelineTooltip.dispose();
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

    async function requestJson(url, signal) {
        const response = await fetch(url, {
            signal,
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

    // Only successful hourly results are cached. A pending request is shared by repeated hovers.
    function createTimelineTooltip(request, now = () => Date.now()) {
        const cache = new Map();
        const pending = new Map();
        let current = null;
        let disposed = false;
        function formatter(params, ticket, callback) {
            const point = Array.isArray(params) ? params[0] : params;
            const hour = point?.axisValue || point?.name || "";
            const count = Number(point?.value || 0);
            current = {hour, count, ticket, callback};
            if (disposed || !hour || !Number.isFinite(count) || count <= 0) {
                return renderTimelineTooltip(hour, 0);
            }
            const cached = cache.get(hour);
            if (cached && now() - cached.loadedAt < 60_000) return renderTimelineTooltip(hour, count, cached.details);
            for (const [key, value] of cache) if (now() - value.loadedAt >= 60_000) cache.delete(key);
            if (!pending.has(hour)) {
                const controller = new AbortController();
                pending.set(hour, controller);
                Promise.resolve().then(() => request('/api/dashboard/hour-artists?' + new URLSearchParams({hour}), controller.signal))
                    .then(result => {
                        if (disposed) return;
                        const details = result.data;
                        cache.set(hour, {loadedAt: now(), details});
                        if (current?.hour === hour && typeof current.callback === "function") {
                            current.callback(current.ticket, renderTimelineTooltip(hour, current.count, details));
                        }
                    }).catch(error => {
                        if (!disposed && error.name !== "AbortError" && current?.hour === hour && typeof current.callback === "function") {
                            current.callback(current.ticket, renderTimelineTooltip(hour, current.count, null, true));
                        }
                    }).finally(() => pending.delete(hour));
            }
            return renderTimelineTooltip(hour, count, null, false, true);
        }
        return {
            formatter,
            dispose() {
                disposed = true;
                current = null;
                for (const controller of pending.values()) controller.abort();
                pending.clear();
                cache.clear();
            }
        };
    }

    function renderTimelineTooltip(hour, count, details, failed = false, loading = false) {
        // Build DOM with textContent so artist names and dates are never interpreted as HTML.
        const root = document.createElement("div");
        root.className = "timeline-tooltip";
        const title = document.createElement("div");
        title.className = "timeline-tooltip-hour";
        title.textContent = hour;
        root.appendChild(title);
        const summary = document.createElement("strong");
        summary.className = "timeline-tooltip-total";
        summary.textContent = '\u6293\u53d6\u8bb0\u5f55 ' + numberFormat.format(details ? details.total : count) + ' \u6761';
        root.appendChild(summary);
        if (loading || failed) {
            const message = document.createElement("div");
            message.className = "timeline-tooltip-message";
            message.textContent = failed ? '\u4f5c\u8005\u660e\u7ec6\u6682\u65f6\u65e0\u6cd5\u52a0\u8f7d' : '\u6b63\u5728\u52a0\u8f7d\u4f5c\u8005\u660e\u7ec6\u2026';
            root.appendChild(message);
        }
        if (!details) return root;
        const list = document.createElement("div");
        list.className = "timeline-tooltip-authors";
        list.setAttribute("role", "list");
        const authors = [...(details.artists || [])];
        if (details.unknownCount > 0) authors.push({name: '\u672a\u77e5\u4f5c\u8005', count: details.unknownCount});
        for (const artist of authors) {
            const row = document.createElement("div");
            row.className = "timeline-tooltip-author";
            row.setAttribute("role", "listitem");
            const name = artist.name || '\u672a\u77e5\u4f5c\u8005';
            const avatar = document.createElement("span");
            avatar.className = "timeline-tooltip-avatar";
            avatar.textContent = Array.from(name)[0] || "?";
            if (typeof artist.icon === "string" && artist.icon.startsWith("/api/fanbox/artist-image/")) {
                const image = document.createElement("img");
                image.alt = "";
                image.src = artist.icon;
                image.addEventListener("error", () => image.remove(), {once: true});
                avatar.appendChild(image);
            }
            row.appendChild(avatar);
            const label = document.createElement("span");
            label.className = "timeline-tooltip-name";
            label.textContent = name;
            label.title = name;
            row.appendChild(label);
            const amount = document.createElement("strong");
            amount.className = "timeline-tooltip-count";
            amount.textContent = numberFormat.format(artist.count || 0) + ' \u6761';
            row.appendChild(amount);
            list.appendChild(row);
        }
        root.appendChild(list);
        return root;
    }

    function timelineOption(rows, tooltipFormatter) {
        return {
            animationDuration: 500,
            color: ["#218ec5"],
            tooltip: {
                trigger: "axis",
                renderMode: "html",
                enterable: true,
                confine: true,
                hideDelay: 200,
                formatter: tooltipFormatter,
                extraCssText: "white-space:normal;max-width:calc(100vw - 32px);",
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
