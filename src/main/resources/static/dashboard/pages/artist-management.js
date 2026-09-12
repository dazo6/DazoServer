(() => {
    "use strict";

    const {onMounted, reactive, ref} = Vue;
    const {useRoute, useRouter} = VueRouter;
    window.DazoAdminPages = window.DazoAdminPages || {};

    window.DazoAdminPages.ArtistManagement = {
        template: "#artist-management-template",
        setup() {
            const route = useRoute();
            const router = useRouter();
            const rows = ref([]);
            const total = ref(0);
            const page = ref(positiveInteger(route.query.page, 1));
            const pageSize = ref(paginationSize(route.query.pageSize));
            const loading = ref(false);
            const creating = ref(false);
            const createDialogVisible = ref(false);
            const updatingKeys = ref(new Set());
            const expandedRows = ref(new Set());
            const filters = reactive({keyword: "", enabled: null, lastUpdateOrder: "desc"});
            const createForm = reactive(defaultCreateForm());

            async function loadArtists(targetPage = page.value) {
                page.value = Number(targetPage) || 1;
                await syncListRoute();
                loading.value = true;
                try {
                    const params = new URLSearchParams({
                        page: String(page.value),
                        pageSize: String(pageSize.value)
                    });
                    if (filters.keyword) params.set("keyword", filters.keyword);
                    if (typeof filters.enabled === "boolean") {
                        params.set("enabled", String(filters.enabled));
                    }
                    params.set("lastUpdateOrder", filters.lastUpdateOrder);
                    const data = await requestData(`/api/fanbox?${params}`);
                    rows.value = data.records || [];
                    expandedRows.value = new Set();
                    total.value = Number(data.total || 0);
                } catch (error) {
                    ElementPlus.ElMessage.error(`画师列表加载失败：${error.message}`);
                } finally {
                    loading.value = false;
                }
            }

            function resetFilters() {
                filters.keyword = "";
                filters.enabled = null;
                filters.lastUpdateOrder = "desc";
                loadArtists(1);
            }

            function hideBrokenImage(row, field) {
                if (row) row[field] = "";
            }

            function isRowExpanded(row) {
                return Boolean(row) && expandedRows.value.has(row.id);
            }

            function toggleRowDetails(row) {
                if (!row) return;
                const next = new Set(expandedRows.value);
                if (next.has(row.id)) next.delete(row.id);
                else next.add(row.id);
                expandedRows.value = next;
            }

            async function openGallery(row) {
                if (!row || row.id === null || row.id === undefined) {
                    ElementPlus.ElMessage.warning("当前画师缺少有效编号，无法打开图库");
                    return;
                }
                await syncListRoute();
                await router.push({
                    path: `/manage/artists/${encodeURIComponent(row.id)}/gallery`,
                    query: {name: row.name || ""}
                });
            }

            function syncListRoute() {
                const nextPage = String(page.value);
                const nextPageSize = String(pageSize.value);
                if (String(route.query.page || "") === nextPage
                    && String(route.query.pageSize || "") === nextPageSize) {
                    return Promise.resolve();
                }
                return router.replace({
                    name: "artist-management",
                    query: {
                        ...route.query,
                        page: nextPage,
                        pageSize: nextPageSize
                    }
                });
            }

            function changePageSize() {
                loadArtists(1);
            }

            function openCreateDialog() {
                Object.assign(createForm, defaultCreateForm());
                createDialogVisible.value = true;
            }

            async function createArtist() {
                if (!createForm.url) {
                    ElementPlus.ElMessage.warning("请输入画师主页 URL");
                    return;
                }
                creating.value = true;
                try {
                    await requestData("/api/fanbox", {
                        method: "POST",
                        headers: {"Content-Type": "application/json", Accept: "application/json"},
                        body: JSON.stringify(createForm)
                    });
                    ElementPlus.ElMessage.success("画师创建成功");
                    createDialogVisible.value = false;
                    await loadArtists(1);
                } catch (error) {
                    ElementPlus.ElMessage.error(`创建失败：${error.message}`);
                } finally {
                    creating.value = false;
                }
            }

            async function updateStatus(row) {
                const nextStatus = Boolean(row.enable);
                const key = `${row.id}:status`;
                setUpdating(key, true);
                try {
                    await requestData(`/api/fanbox/${row.id}/status?enabled=${nextStatus}`, {
                        method: "POST",
                        headers: {Accept: "application/json"}
                    });
                    ElementPlus.ElMessage.success(nextStatus ? "已启用" : "已停用");
                } catch (error) {
                    row.enable = !nextStatus;
                    ElementPlus.ElMessage.error(`状态更新失败：${error.message}`);
                } finally {
                    setUpdating(key, false);
                }
            }

            async function toggleCompression(row) {
                const nextStatus = !Boolean(row.compression);
                const key = `${row.id}:compression`;
                if (updatingKeys.value.has(key)) return;
                setUpdating(key, true);
                try {
                    await requestData(`/api/fanbox/${row.id}/compression?enabled=${nextStatus}`, {
                        method: "POST",
                        headers: {Accept: "application/json"}
                    });
                    row.compression = nextStatus;
                    ElementPlus.ElMessage.success(nextStatus ? "已开启压缩" : "已关闭压缩");
                } catch (error) {
                    ElementPlus.ElMessage.error(`压缩设置更新失败：${error.message}`);
                } finally {
                    setUpdating(key, false);
                }
            }

            function setUpdating(key, active) {
                const next = new Set(updatingKeys.value);
                if (active) next.add(key);
                else next.delete(key);
                updatingKeys.value = next;
            }

            onMounted(loadArtists);

            return {
                changePageSize,
                createArtist,
                createDialogVisible,
                createForm,
                creating,
                filters,
                hideBrokenImage,
                isRowExpanded,
                loadArtists,
                loading,
                openCreateDialog,
                openGallery,
                page,
                pageSize,
                resetFilters,
                rows,
                total,
                toggleCompression,
                toggleRowDetails,
                updateStatus,
                updatingKeys
            };
        }
    };

    function defaultCreateForm() {
        return {url: "", name: "", compression: true};
    }

    function positiveInteger(value, fallback) {
        const parsed = Number.parseInt(value, 10);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
    }

    function paginationSize(value) {
        const parsed = positiveInteger(value, 20);
        return [10, 20, 50, 100].includes(parsed) ? parsed : 20;
    }

    async function requestData(url, options = {}) {
        const response = await fetch(url, options);
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
