const assert=require("node:assert/strict");
const fs=require("node:fs"),vm=require("node:vm");
const pageSource=fs.readFileSync("src/main/resources/static/dashboard/pages/scheduled-tasks.js","utf8");
const routerSource=fs.readFileSync("src/main/resources/static/dashboard/app.js","utf8");
(async()=>{
    let mounted,unmounted,timer,aborted=false;
    let requests=[],pending;
    const auth={request:async(url,options)=>{
        requests.push({url,options});
        if(options.method==="POST") return new Promise(resolve=>pending=resolve);
        options.signal.addEventListener("abort",()=>aborted=true);
        return [{id:"test",name:"tick",running:false,manualEnabled:true}];
    }};
    const sandbox={window:{DazoAuth:auth},Vue:{ref:value=>({value}),onMounted:f=>mounted=f,onUnmounted:f=>unmounted=f},
        ElementPlus:{ElMessage:{success(){},error(){}}},AbortController,
        setTimeout:f=>{timer=f;return 1;},clearTimeout(){timer=null;},Date};
    vm.runInNewContext(pageSource,sandbox);
    const component=sandbox.window.DazoAdminPages.ScheduledTasks,state=component.setup();
    mounted();await new Promise(resolve=>setImmediate(resolve));
    assert.equal(state.tasks.value.length,1);assert.equal(requests.length,1);
    assert.equal(typeof timer,"function");
    const task=state.tasks.value[0],run=state.run(task);
    state.run(task);assert.equal(requests.filter(r=>r.options.method==="POST").length,1);
    pending();await run;await new Promise(resolve=>setImmediate(resolve));
    state.run({...task,running:true});
    assert.equal(requests.filter(r=>r.options.method==="POST").length,1);
    unmounted();assert.equal(timer,null);assert.equal(aborted,true);
    assert.ok(component.template.includes(':disabled="task.running'));
    assert.ok(!component.template.includes("v-html"));
    let guard;
    const routerContext={window:{DazoAdminPages:{},DazoAuth:{ready:Promise.resolve(),state:{user:{isSuperAdmin:false}},can:()=>true}},
        Vue:{createApp:()=>({use(){return this;},mount(){}})},VueRouter:{createWebHashHistory:()=>({}),createRouter:()=>({beforeEach:f=>guard=f})},ElementPlus:{}};
    vm.runInNewContext(routerSource,routerContext);
    assert.equal(await guard({name:"scheduled-tasks"}),"/manage/artists");
    routerContext.window.DazoAuth.state.user.isSuperAdmin=true;
    assert.equal(await guard({name:"scheduled-tasks"}),true);
    console.log("scheduled task polling, duplicate clicks, cleanup and super-admin route checks passed");
})().catch(e=>{console.error(e);process.exitCode=1;});
