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
                {path: "history", name: "crawl-history", component: pages.CrawlHistory}
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

    createApp({
        setup() {
            async function logout() {
                try {
                    await fetch("/api/auth/logout", {method: "POST", credentials: "same-origin"});
                } finally {
                    location.replace("/login.html");
                }
            }

            return {logout};
        }
    }).use(router).use(ElementPlus).mount("#app");
})();
