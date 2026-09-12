(() => {
    "use strict";

    const {onMounted, reactive, ref} = Vue;
    window.DazoAdminPages = window.DazoAdminPages || {};

    window.DazoAdminPages.CrawlHistory = {
        template: "#crawl-history-template",
        setup() {
            const rows = ref([]);
            const total = ref(0);
            const page = ref(1);
            const pageSize = ref(20);
            const loading = ref(false);
            const filters = reactive({keyword: "", done: null, dateRange: []});

            async function loadHistory(targetPage = page.value) {
                page.value = Number(targetPage) || 1;
                loading.value = true;
                try {
                    const params = new URLSearchParams({
                        page: String(page.value),
                        pageSize: String(pageSize.value)
                    });
                    if (filters.keyword) params.set("keyword", filters.keyword);
                    if (typeof filters.done === "boolean") params.set("done", String(filters.done));
                    if (filters.dateRange && filters.dateRange.length === 2) {
                        params.set("start", filters.dateRange[0]);
                        params.set("end", filters.dateRange[1]);
                    }
                    const data = await requestData(`/api/crawler-requests?${params}`);
                    rows.value = data.records || [];
                    total.value = Number(data.total || 0);
                } catch (error) {
                    ElementPlus.ElMessage.error(`历史记录加载失败：${error.message}`);
                } finally {
                    loading.value = false;
                }
            }

            function resetFilters() {
                filters.keyword = "";
                filters.done = null;
                filters.dateRange = [];
                loadHistory(1);
            }

            function changePageSize() {
                loadHistory(1);
            }

            onMounted(loadHistory);

            return {
                changePageSize,
                filters,
                loadHistory,
                loading,
                page,
                pageSize,
                resetFilters,
                rows,
                total
            };
        }
    };

    async function requestData(url) {
        const response = await fetch(url, {headers: {Accept: "application/json"}});
        redirectWhenUnauthorized(response);
        let result;
        try {
            result = await response.json();
        } catch (error) {
            throw new Error(`HTTP ${response.status}`);
        }
        if (!response.ok || result.result !== "SUCCESS") {
            throw new Error(result.message || `HTTP ${response.status}`);
        }
        return result.data;
    }

    function redirectWhenUnauthorized(response) {
        if (response.status === 401) {
            const target = location.pathname + location.search + location.hash;
            location.replace(`/login.html?redirect=${encodeURIComponent(target)}`);
        }
    }
})();
