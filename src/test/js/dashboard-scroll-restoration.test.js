const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

let routerOptions;
const context = {
    window: {DazoAdminPages: {}},
    Vue: {
        createApp: () => ({
            use() {
                return this;
            }, mount() {
            }
        })
    },
    VueRouter: {
        createWebHashHistory: () => ({}),
        createRouter: options => {
            routerOptions = options;
            return {};
        }
    },
    ElementPlus: {}
};
const source = fs.readFileSync(path.resolve(__dirname,
    "../../main/resources/static/dashboard/app.js"), "utf8");
vm.runInNewContext(source, context);

const behavior = routerOptions.scrollBehavior;
const coordinator = context.window.DazoAdminScrollRestoration;
const artists = {name: "artist-management", fullPath: "/manage/artists?page=2"};
const gallery = {name: "artist-gallery", fullPath: "/manage/gallery?folderPath=album"};
const home = {name: "dashboard-home", fullPath: "/"};
const position = {left: 0, top: 780};

assert.equal(behavior(gallery, artists, null).top, 0);
let restored;
assert.equal(behavior(artists, gallery, position), false);
assert.equal(coordinator.contentReady(artists.fullPath, value => {
    restored = value;
}), true);
assert.equal(restored, position);

assert.equal(behavior(gallery, artists, null).top, 0);
assert.equal(coordinator.contentReady(gallery.fullPath, value => {
    restored = value;
}), false);
assert.equal(behavior(gallery, artists, position), false);
assert.equal(restored, position);

assert.equal(behavior(gallery, gallery, position), position);
assert.equal(behavior(home, gallery, position), position);
