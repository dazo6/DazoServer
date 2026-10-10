const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
(async () => {
let mounted, unmounted, options, instance, played=0, paused=0, destroyed=0;
const listeners={}, emitted=[];
const video={videoWidth:1920,videoHeight:1080};
const context={Vue:{ref:()=>({value:{}}),onMounted:fn=>mounted=fn,onBeforeUnmount:fn=>unmounted=fn},VueRouter:{},window:{DazoAdminPages:{},Player:class {constructor(config){options=config;this.video=video;instance=this;this.muted=false;} on(name,fn){listeners[name]=fn;} play(){played++;return Promise.resolve();} pause(){paused++;} destroy(){destroyed++;}}}};
vm.runInNewContext(fs.readFileSync('src/main/resources/static/dashboard/pages/artist-gallery.js','utf8'),context);
const component=context.window.DazoAdminPages.ArtistGallery.components.GalleryVideoPlayer;
component.setup({slide:{image:{url:'/video.mp4',thumbnailUrl:'/poster.jpg'}}},{emit:(...args)=>emitted.push(args)});
mounted();
assert.equal(options.url,'/video.mp4');
assert.equal(options.playsinline,true);
assert.equal(options.autoplay,true);
assert.equal(options.videoAttributes.preload,'metadata');
listeners.loadedmetadata();assert.equal(emitted[0][1].currentTarget,video);
listeners.error();assert.equal(emitted[1][0],'error');
listeners.autoplay_was_prevented();
assert.equal(played,0); // Retry must wait until xgplayer finishes its pause handler.
await Promise.resolve();
assert.equal(instance.muted,true);assert.equal(played,1);
listeners.autoplay_was_prevented();await Promise.resolve();assert.equal(played,1);
instance.muted=false;listeners.autoplay_was_prevented();
unmounted();await Promise.resolve();assert.equal(played,1);
assert.equal(paused,1);assert.equal(destroyed,1);
context.window.Player=class {constructor(){throw Error('unavailable');}};
component.setup({slide:{image:{url:'/video.mp4'}}},{emit:(...args)=>emitted.push(args)});
mounted();assert.equal(emitted[2][0],'error');unmounted();
console.log('gallery video lifecycle checks passed');

})().catch(error=>{console.error(error);process.exitCode=1;});

// Compact API items must reconstruct encoded media URLs before entering the viewer.
{
    const source = fs.readFileSync('src/main/resources/static/dashboard/pages/artist-gallery.js', 'utf8');
    const start = source.indexOf('                            if (image.type !== "folder") {');
    const end = source.indexOf('                            const old=', start);
    assert.ok(start >= 0 && end > start);
    const folderPath = {value: 'D:/??/album & +#'};
    const hydrate = new Function('image', 'folderPath', 'URLSearchParams', source.slice(start, end) + 'return image;');
    for (const type of ['image', 'video']) {
        const item = hydrate({name: '???/a &+#?.mp4', type}, folderPath, URLSearchParams);
        const url = new URL(item.url, 'http://localhost');
        assert.equal(url.pathname, '/api/fanbox/gallery/file');
        assert.equal(url.searchParams.get('folderPath'), folderPath.value);
        assert.equal(url.searchParams.get('path'), item.name);
        assert.equal(new URL(item.thumbnailUrl, url).searchParams.get('thumbnail'), 'true');
    }
    const folder = {name: 'album', type: 'folder', posterUrl: null};
    assert.equal(hydrate(folder, folderPath, URLSearchParams), folder);
    assert.equal(folder.posterUrl, null);
}
