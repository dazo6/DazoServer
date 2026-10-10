const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
class Element {
    constructor(tag) { this.tagName = tag; this.children = []; this.events = {}; this.ownText = ''; }
    set textContent(value) { this.ownText = String(value); this.children = []; }
    get textContent() { return this.ownText + this.children.map(child => child.textContent).join(''); }
    appendChild(child) { child.parent = this; this.children.push(child); return child; }
    setAttribute(name, value) { this[name] = value; }
    addEventListener(name, callback) { this.events[name] = callback; }
    remove() { if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this); }
}
const context = {
    Vue: {}, window: {DazoAdminPages: {}}, AbortController, URLSearchParams,
    document: {createElement: tag => new Element(tag)},
    echarts: {graphic: {LinearGradient: function () {}}}
};
let source = fs.readFileSync('src/main/resources/static/dashboard/pages/dashboard-home.js', 'utf8');
source = source.replace(/\}\)\(\);\s*$/, 'window.testTimeline = {createTimelineTooltip, timelineOption};})();');
vm.runInNewContext(source, context);
const {createTimelineTooltip, timelineOption} = context.window.testTimeline;
const flush = () => new Promise(resolve => setImmediate(resolve));
const point = (hour, value) => [{axisValue: hour, value}];
const hourA = '2026-10-07 10:00', hourB = '2026-10-07 11:00';
const calls = [], replies = [];
let now = 0;
const tooltip = createTimelineTooltip((url, signal) => new Promise((resolve, reject) => calls.push({url, signal, resolve, reject})), () => now);
const callback = (ticket, dom) => replies.push({ticket, dom});
const details = {total: 7, unknownCount: 2, artists: [{artistId: 7, name: '<img src=x onerror=alert(1)>', icon: '/api/fanbox/artist-image/artist-7-icon-test.jpg', count: 5}]};
(async () => {
    const option = timelineOption([], tooltip.formatter);
    assert.equal(option.tooltip.trigger, 'axis');
    assert.equal(option.tooltip.enterable, true);
    assert.equal(option.tooltip.renderMode, 'html');
    tooltip.formatter(point(hourA, 0), 'zero', callback);
    await flush(); assert.equal(calls.length, 0);
    const loading = tooltip.formatter(point(hourA, 7), 'a1', callback);
    assert.ok(loading.textContent.includes('7'));
    tooltip.formatter(point(hourA, 7), 'a2', callback);
    await flush(); assert.equal(calls.length, 1);
    const url = new URL(calls[0].url, 'http://localhost');
    assert.equal(url.pathname, '/api/dashboard/hour-artists');
    assert.equal(url.searchParams.get('hour'), hourA);
    tooltip.formatter(point(hourB, 2), 'b', callback);
    await flush(); assert.equal(calls.length, 2);
    calls[0].resolve({data: details}); await flush();
    assert.equal(replies.length, 0); // A must not replace B's hover.
    calls[1].resolve({data: {total: 2, unknownCount: 2, artists: []}}); await flush();
    assert.equal(replies.length, 1); assert.equal(replies[0].ticket, 'b');
    const cached = tooltip.formatter(point(hourA, 7), 'cached', callback);
    await flush(); assert.equal(calls.length, 2);
    assert.ok(cached.textContent.includes('<img src=x onerror=alert(1)>'));
    const list = cached.children.find(child => child.className === 'timeline-tooltip-authors');
    assert.equal(list.children.length, 2); // Known author plus unresolved records.
    const label = list.children[0].children.find(child => child.className === 'timeline-tooltip-name');
    assert.equal(label.children.length, 0); // Name is text rather than interpreted markup.
    const avatar = list.children[0].children[0];
    assert.equal(avatar.children[0].tagName, 'img');
    avatar.children[0].events.error(); assert.equal(avatar.children.length, 0);
    now = 60_000;
    tooltip.formatter(point(hourA, 7), 'expired', callback);
    await flush(); assert.equal(calls.length, 3);
    calls[2].reject(new Error('network')); await flush();
    assert.equal(replies[1].ticket, 'expired');
    assert.ok(replies[1].dom.children.some(child => child.className === 'timeline-tooltip-message'));
    tooltip.formatter(point(hourA, 7), 'retry', callback);
    await flush(); assert.equal(calls.length, 4); // Errors do not poison the cache.
    tooltip.dispose(); assert.equal(calls[3].signal.aborted, true);
    calls[3].resolve({data: details}); await flush(); assert.equal(replies.length, 2);
    console.log('dashboard timeline tooltip checks passed');
})().catch(error => {console.error(error); process.exitCode = 1;});
