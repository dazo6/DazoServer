(() => {
    "use strict";

    const {createApp} = Vue;
    const {createRouter, createWebHashHistory} = VueRouter;
    const pages = window.DazoAdminPages;
    const scrollRestoration = {
        pending: null,
        ready: null,
        contentReady(fullPath, restore) {
            if (this.pending && this.pending.fullPath === fullPath) {
                const position = this.pending.position;
                this.pending = null;
                this.ready = null;
                restore(position);
                return true;
            }
            this.ready = {fullPath, restore};
            return false;
        }
    };
    window.DazoAdminScrollRestoration = scrollRestoration;

    const routes = [
        {path: "/", name: "dashboard-home", component: pages.DashboardHome},
        {
            path: "/manage",
            children: [
                {path: "artists", name: "artist-management", component: pages.ArtistManagement},
                {path: "free-galleries", name: "free-gallery", component: pages.FreeGallery},
                {path: "gallery", name: "artist-gallery", component: pages.ArtistGallery},
                {path: "history", name: "crawl-history", component: pages.CrawlHistory},
                {path: "scheduled-tasks", name: "scheduled-tasks", component: pages.ScheduledTasks},
                {path: "users", name: "user-management", component: pages.UserManagement},
                {path: "account", name: "my-account", component: pages.MyAccount}
            ]
        },
        {path: "/:pathMatch(.*)*", redirect: "/"}
    ];

    const router = createRouter({
        history: createWebHashHistory(),
        routes,
        scrollBehavior(to, from, savedPosition) {
            if (!savedPosition) {
                scrollRestoration.pending = null;
                scrollRestoration.ready = null;
                return {top: 0};
            }
            if (to.fullPath === from.fullPath) return savedPosition;
            if (!["artist-management", "free-gallery", "artist-gallery"].includes(to.name)) {
                scrollRestoration.pending = null;
                scrollRestoration.ready = null;
                return savedPosition;
            }
            const ready = scrollRestoration.ready;
            scrollRestoration.ready = null;
            scrollRestoration.pending = null;
            if (ready && ready.fullPath === to.fullPath) {
                ready.restore(savedPosition);
            } else {
                scrollRestoration.pending = {fullPath: to.fullPath, position: savedPosition};
            }
            return false;
        }
    });

    if(router.beforeEach) router.beforeEach(async to => {
        await window.DazoAuth.ready;
        if(to.name==="scheduled-tasks" && !window.DazoAuth.state.user?.isSuperAdmin) return "/manage/artists";
        if(to.name==="user-management" && !window.DazoAuth.can("read","op:user.list")) return "/manage/artists";
        if(to.name==="crawl-history" && !window.DazoAuth.can("read","*")) return "/manage/artists";
        return true;
    });

    createApp({
        setup() {
            return {authState: window.DazoAuth.state, can: window.DazoAuth.can};
        }
    }).use(router).use(ElementPlus).mount("#app");
})();
