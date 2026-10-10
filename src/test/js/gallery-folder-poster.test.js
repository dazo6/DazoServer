const assert = require('node:assert/strict');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/resources/static/dashboard/pages/artist-gallery.js', 'utf8');
function bodyOf(name) {
    const start = source.indexOf('function ' + name + '(');
    assert.ok(start >= 0);
    const body = source.indexOf('{', start) + 1;
    const end = source.indexOf('\n            }', body);
    return source.slice(body, end);
}
const posters = [{naturalWidth: 480, naturalHeight: 800}, {naturalWidth: 900, naturalHeight: 300}];
const items = posters.map((poster, index) => ({
    dataset: {galleryIndex: String(index)}, style: {},
    querySelector: () => poster,
    getBoundingClientRect: () => ({width: 320})
}));
const grid = {querySelectorAll: () => items};
const images = {value: posters.map(() => ({type: 'folder', posterUrl: '/poster.png'}))};
const layout = new Function('galleryGrid', 'images', 'window', 'activeRestorePosition', 'alignRestoredGalleryPosition', 'let layoutFrame; ' + bodyOf('layoutGalleryItems'));
layout({value: grid}, images, {getComputedStyle: () => ({gridAutoRows: '8', rowGap: '16'})}, null, () => {});
assert.ok(parseInt(items[0].style.height) > parseInt(items[1].style.height));
assert.ok(parseInt(items[1].style.height) >= 180);
assert.notEqual(items[0].style.gridRowEnd, items[1].style.gridRowEnd);
let scheduled = 0;
const markLoaded = new Function('event', 'image', 'scheduleGalleryLayout', bodyOf('markImageLoaded'));
const image = {type: 'folder'};
markLoaded({currentTarget: posters[0]}, image, () => scheduled++);
assert.equal(image.aspectRatio, 480 / 800);
assert.equal(image.loaded, true);
assert.equal(scheduled, 1);
const css = fs.readFileSync('src/main/resources/static/dashboard/dashboard.css', 'utf8');
const rule = css.match(/\.gallery-folder-visual \.gallery-folder-poster-image\s*\{([^}]+)\}/);
assert.ok(rule && rule[1].includes('object-fit: contain'));
console.log('gallery folder poster layout checks passed');
