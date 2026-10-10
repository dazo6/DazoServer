const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm");
const source=fs.readFileSync("src/main/resources/static/dashboard/pages/artist-gallery.js","utf8");
const start=source.indexOf("            const downloading=ref(false);");
const end=source.indexOf("            function sortStorageKey",start);
assert.ok(start>=0 && end>start);
function create(request,allowed=true){
    let clicks=0,errors=[],links=[];
    const context={ref:value=>({value}),computed:fn=>({get value(){return fn();}}),folderPath:{value:"D:/art/画师 & #"},
        window:{DazoAuth:{can:()=>allowed,request}},AbortController,URLSearchParams,uploadDisposed:false,
        ElementPlus:{ElMessage:{error:message=>errors.push(message)}},
        document:{createElement:()=>({click(){clicks++;},remove(){this.removed=true;}}),body:{appendChild:link=>links.push(link)}}};
    vm.createContext(context);vm.runInContext(source.slice(start,end)+";this.state={downloading,canDownload,downloadAll};",context);
    return {context,state:context.state,links,errors,clicks:()=>clicks};
}
(async()=>{
    let count=0,resolve,options,url;
    const test=create((u,o)=>{count++;url=u;options=o;return new Promise(r=>resolve=r);});
    const first=test.state.downloadAll();await test.state.downloadAll();
    assert.equal(count,1);assert.equal(test.state.downloading.value,true);
    assert.equal(new URLSearchParams(url.split("?")[1]).get("folderPath"),test.context.folderPath.value);
    resolve({url:"/api/fanbox/gallery/archive/download?folderPath=x",filename:"画师.zip"});await first;
    assert.equal(test.clicks(),1);assert.equal(test.links[0].download,"画师.zip");assert.equal(test.links[0].removed,true);
    assert.equal(test.state.downloading.value,false);
    const denied=create(()=>{throw Error("should not request");},false);await denied.state.downloadAll();assert.equal(denied.clicks(),0);
    const large=create(async()=>{throw Error("300 files maximum");});await large.state.downloadAll();assert.equal(large.errors[0],"300 files maximum");assert.equal(large.clicks(),0);assert.equal(large.state.downloading.value,false);
    let done;
    const changed=create(()=>new Promise(r=>done=r));const pending=changed.state.downloadAll();changed.context.folderPath.value="other";
    done({url:"/archive",filename:"old.zip"});await pending;assert.equal(changed.clicks(),0);
    let complete;
    const disposed=create(()=>new Promise(r=>complete=r));const pendingDisposed=disposed.state.downloadAll();disposed.context.uploadDisposed=true;
    complete({url:"/archive",filename:"old.zip"});await pendingDisposed;assert.equal(disposed.clicks(),0);
    const html=fs.readFileSync("src/main/resources/static/dashboard/index.html","utf8");
    const actions=html.slice(html.indexOf('<div class="gallery-upload-actions">'),html.indexOf('<div class="gallery-actions">'));
    assert.ok(actions.includes('@click="uploadInput.click()"') && actions.includes('@click="downloadAll"'));
    assert.ok(actions.includes('downloading || !canDownload'));
    console.log("gallery archive download, duplicate click, read permission, errors and route cleanup checks passed");
})().catch(e=>{console.error(e);process.exitCode=1;});
