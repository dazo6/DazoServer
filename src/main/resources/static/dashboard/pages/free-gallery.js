(() => {
    "use strict";

    const {nextTick, onMounted, reactive, ref} = Vue;
    const {useRoute, useRouter} = VueRouter;
    window.DazoAdminPages = window.DazoAdminPages || {};

    window.DazoAdminPages.FreeGallery = {
        template: "#free-gallery-template",
        setup() {
            const route = useRoute();
            const router = useRouter();
            const rows = ref([]);
            const loading = ref(false);
            const saving = ref(false);
            const dialogVisible = ref(false);
            const editingId = ref(null);
            const keyword = ref("");
            const form = reactive({folderPath: "", name: ""});

            async function loadGalleries() {
                loading.value = true;
                try {
                    const params = new URLSearchParams();
                    if (keyword.value.trim()) params.set("keyword", keyword.value.trim());
                    rows.value = await requestData(`/api/free-galleries?${params}`) || [];
                } catch (error) {
                    ElementPlus.ElMessage.error(`自由画廊加载失败：${error.message}`);
                } finally {
                    loading.value = false;
                    await nextTick();
                    if (route.name === "free-gallery") {
                        window.DazoAdminScrollRestoration?.contentReady(route.fullPath, position => {
                            window.scrollTo(position.left || 0, position.top || 0);
                        });
                    }
                }
            }

            function resetSearch() {
                keyword.value = "";
                loadGalleries();
            }

            function openCreateDialog() {
                editingId.value = null;
                form.folderPath = "";
                form.name = "";
                dialogVisible.value = true;
            }

            function openEditDialog(row) {
                editingId.value = row.id;
                form.folderPath = row.folderPath || "";
                form.name = row.name || "";
                dialogVisible.value = true;
            }

            async function saveGallery() {
                if (!form.folderPath.trim()) {
                    ElementPlus.ElMessage.warning("请输入文件夹绝对路径");
                    return;
                }
                saving.value = true;
                try {
                    const editing = editingId.value !== null;
                    await requestData(editing ? `/api/free-galleries/${editingId.value}` : "/api/free-galleries", {
                        method: editing ? "PUT" : "POST",
                        headers: {"Content-Type": "application/json", Accept: "application/json"},
                        body: JSON.stringify({
                            folderPath: form.folderPath.trim(),
                            name: form.name.trim()
                        })
                    });
                    ElementPlus.ElMessage.success(editing ? "自由画廊已更新" : "自由画廊已注册");
                    dialogVisible.value = false;
                    await loadGalleries();
                } catch (error) {
                    ElementPlus.ElMessage.error(`保存失败：${error.message}`);
                } finally {
                    saving.value = false;
                }
            }

            async function removeGallery(row) {
                try {
                    await ElementPlus.ElMessageBox.confirm(
                        `确定删除自由画廊“${row.name}”吗？仅删除登记信息，不会删除文件夹或文件。`,
                        "删除自由画廊",
                        {type: "warning", confirmButtonText: "删除", cancelButtonText: "取消"}
                    );
                    await requestData(`/api/free-galleries/${row.id}`, {method: "DELETE"});
                    ElementPlus.ElMessage.success("自由画廊已删除");
                    await loadGalleries();
                } catch (error) {
                    if (error === "cancel" || error === "close") return;
                    ElementPlus.ElMessage.error(`删除失败：${error.message || error}`);
                }
            }

            function canGallery(row,type) {
                const action=type==="read"?null:"op:freeGallery."+type;
                return window.DazoAuth.can(type,row.folderPath) && (!action || window.DazoAuth.can(type,action));
            }
            function openGallery(row) {
                if(!canGallery(row,"read")) return;
                router.push({
                    name: "artist-gallery",
                    query: {folderPath: row.folderPath}
                });
            }

            onMounted(loadGalleries);

            return {
                can: window.DazoAuth.can,
                permissionRowClass: ({row}) => canGallery(row,"read") ? "" : "permission-masked",
                canGallery,
                dialogVisible,
                editingId,
                form,
                keyword,
                loadGalleries,
                loading,
                openCreateDialog,
                openEditDialog,
                openGallery,
                removeGallery,
                resetSearch,
                rows,
                saveGallery,
                saving
            };
        }
    };

    async function requestData(url, options = {}) {
        const response = await fetch(url, options);
        if (response.status === 401) {
            const target = location.pathname + location.search + location.hash;
            location.replace(`/login.html?redirect=${encodeURIComponent(target)}`);
        }
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
})();
