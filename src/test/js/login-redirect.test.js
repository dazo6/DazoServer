const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm");
const html=fs.readFileSync("src/main/resources/static/login.html","utf8");
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
async function submit(url,success=true){
    const location=new URL(url,"http://localhost"),navigations=[];
    location.replace=target=>navigations.push(target);
    let listener;
    const error={textContent:""},button={disabled:false,textContent:""};
    const form={elements:{userId:{value:"test"},password:{value:"test1234"}},addEventListener:(name,fn)=>listener=fn};
    const context={URL,URLSearchParams,location,document:{getElementById:id=>id==="login-form"?form:id==="error-message"?error:button},
        fetch:async()=>({ok:success,json:async()=>({result:success?"SUCCESS":"FAILED",message:"Wrong credentials"})})};
    vm.runInNewContext(script,context);await listener({preventDefault(){}});
    return {navigations,error,button};
}
(async()=>{
    const route="/dashboard/#/manage/gallery?folderPath=D%3A%2Fart%2F%20%26%23&sort=name";
    assert.equal((await submit("/login.html?redirect="+encodeURIComponent(route))).navigations[0],route);
    assert.equal((await submit("/login.html?redirect=%2Fdashboard%2F#/manage/artists?keyword=abc")).navigations[0],"/dashboard/#/manage/artists?keyword=abc");
    assert.equal((await submit("/login.html?redirect=/dashboard/#/manage/free-galleries")).navigations[0],"/dashboard/#/manage/free-galleries");
    assert.equal((await submit("/login.html?redirect="+encodeURIComponent("/dashboard/index.html?foo=1")+"#/manage/history")).navigations[0],"/dashboard/index.html?foo=1#/manage/history");
    assert.equal((await submit("/login.html?redirect="+encodeURIComponent("/dashboard/#/manage/artists")+"#/manage/history")).navigations[0],"/dashboard/#/manage/artists");
    assert.equal((await submit("/login.html")).navigations[0],"/dashboard/");
    for(const target of ["//evil.example","/\\evil.example","https://evil.example","javascript:alert(1)","/login.html?redirect=/dashboard/","/"+String.fromCharCode(10)+"/evil.example"])
        assert.equal((await submit("/login.html?redirect="+encodeURIComponent(target))).navigations[0],"/dashboard/");
    const failed=await submit("/login.html?redirect=%2Fdashboard%2F",false);assert.equal(failed.navigations.length,0);assert.equal(failed.button.disabled,false);assert.equal(failed.error.textContent,"Wrong credentials");
    const authSource=fs.readFileSync("src/main/resources/static/dashboard/auth.js","utf8");
    async function authContext(initial401){
        const calls=[],navigations=[],errors=[];
        const location=new URL("http://localhost/dashboard/?theme=light#/manage/gallery?folderPath=D%3A%2Fart%2Fone%20two&sort=name");location.replace=target=>navigations.push(target);
        const window={fetch:async(url)=>{calls.push(url);if(initial401 || String(url).includes('/gallery'))return {status:401,ok:false,json:async()=>({result:"FAILED"})};return {status:200,ok:true,json:async()=>({result:"SUCCESS",data:url.endsWith('/me')?{userId:"root",isSuperAdmin:true}:"csrf"})};},addEventListener(){}};
        const context={window,Vue:{reactive:x=>x},ElementPlus:{ElMessage:{error:x=>errors.push(x)}},location,URL,Headers,Request,Date};vm.runInNewContext(authSource,context);await window.DazoAuth.ready;
        return {window,location,calls,navigations,errors};
    }
    for(const initial401 of [true,false]){
        const context=await authContext(initial401);
        if(!initial401){await context.window.fetch('/api/fanbox/gallery');await context.window.fetch('/api/fanbox/gallery');}
        assert.equal(context.navigations.length,1);
        const login=new URL(context.navigations[0],context.location.origin);
        const original=context.location.pathname+context.location.search+context.location.hash;
        assert.equal(login.searchParams.get('redirect'),original);
        assert.equal((await submit(login.href)).navigations[0],original);
        if(initial401){assert.deepEqual(context.calls,['/api/auth/me']);assert.equal(context.errors.length,0);}
    }
    console.log("Login redirect fragments, query parameters, expired sessions, duplicate redirects and unsafe targets passed");
})().catch(e=>{console.error(e);process.exitCode=1;});
