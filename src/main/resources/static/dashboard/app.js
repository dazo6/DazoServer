(() => {
    "use strict";

    const {createApp} = Vue;
    const {createRouter, createWebHashHistory} = VueRouter;
    const pages = window.DazoAdminPages;

    const routes = [
        {path: "/", name: "dashboard-home", component: pages.DashboardHome},
        {
            path: "/manage",
            children: [
                {path: "artists", name: "artist-management", component: pages.ArtistManagement},
                {path: "artists/:id/gallery", name: "artist-gallery", component: pages.ArtistGallery},
                {path: "history", name: "crawl-history", component: pages.CrawlHistory}
            ]
        },
        {path: "/:pathMatch(.*)*", redirect: "/"}
    ];

    const router = createRouter({
        history: createWebHashHistory(),
        routes,
        scrollBehavior: () => ({top: 0})
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
