(() => {
    "use strict";
    const originalFetch = window.fetch.bind(window);
    const state = Vue.reactive({user: null, csrf: "", ready: false, avatarStamp: Date.now()});
    function canonical(point) {
        point = String(point || "").trim().replace(/\\/g, "/");
        if(point!=="/" && !/^[a-z]:\/$/i.test(point)) point=point.replace(/\/+$/, "");
        return point.startsWith("op:") || point === "*" ? point : point.toLowerCase();
    }
    function match(pattern, point) {
        pattern=canonical(pattern); point=canonical(point);
        let i=0,j=0,star=-1,mark=0;
        while(j<point.length) {
            if(i<pattern.length && pattern[i]===point[j]) {i++;j++;}
            else if(pattern[i]==="*") {star=i++;mark=j;}
            else if(star>=0) {i=star+1;j=++mark;}
            else return false;
        }
        while(pattern[i]==="*") i++;
        return i===pattern.length;
    }
    function can(type, point) {
        if(!state.user) return false;
        return state.user.isSuperAdmin || (state.user.permissions || []).some(rule =>
            rule.startsWith(type+":") && match(rule.slice(type.length+1), point));
    }
    let loginRedirecting=false;
    function redirectToLogin() {
        if(loginRedirecting) return;
        loginRedirecting=true;
        const current=new URL(location.href);
        const target=current.pathname+current.search+current.hash;
        location.replace("/login.html?redirect="+encodeURIComponent(target));
    }
    async function refresh() {
        const response=await originalFetch("/api/auth/me",{credentials:"same-origin"});
        if(response.status===401) {state.user=null;redirectToLogin();return;}
        const result=await response.json();
        if(!response.ok || result.result!=="SUCCESS") throw new Error(result.message || "用户信息加载失败");
        state.user=result.data;
    }
    function refreshAvatar() {
        state.avatarStamp=Date.now();
        let icon=document.querySelector('link[rel="icon"]');
        if(!icon) {icon=document.createElement("link");icon.rel="icon";document.head.appendChild(icon);}
        icon.href="/favicon.ico?t="+state.avatarStamp;
    }
    const ready = (async () => {
        await refresh();
        if(!state.user) return;
        const r=await originalFetch("/api/auth/csrf",{credentials:"same-origin"});
        const body=await r.json();
        if(!r.ok || body.result!=="SUCCESS") throw new Error("会话加载失败，请重新登录");
        state.csrf=body.data;state.ready=true;
    })();
    ready.catch(error => ElementPlus.ElMessage.error(error.message));
    window.fetch = async (input, options={}) => {
        await ready;
        const requestUrl=new URL(typeof input==="string"?input:input.url,location.href);
        const method=(options.method || (input instanceof Request?input.method:"GET")).toUpperCase();
        if(requestUrl.origin===location.origin && !["GET","HEAD","OPTIONS"].includes(method)) {
            const headers=new Headers(options.headers || (input instanceof Request?input.headers:undefined));
            headers.set("X-CSRF-Token",state.csrf);
            options={...options,headers,credentials:"same-origin"};
        }
        const response=await originalFetch(input,options);
        if(requestUrl.origin===location.origin && response.status===403) refresh().catch(()=>{});
        if(requestUrl.origin===location.origin && response.status===401) {
            state.user=null;redirectToLogin();
        }
        return response;
    };
    async function request(url,options={}) {
        const r=await window.fetch(url,options);
        const b=await r.json();
        if(!r.ok || b.result!=="SUCCESS") throw new Error(b.message || "HTTP "+r.status);
        return b.data;
    }
    async function logout() {
        try { await window.fetch("/api/auth/logout", {method:"POST",credentials:"same-origin"}); }
        finally { location.replace("/login.html"); }
    }
    window.DazoAuth={state,ready,refresh,refreshAvatar,can,match,request,logout,
        json(method,body){return {method,headers:{"Content-Type":"application/json"},body:JSON.stringify(body)};}};
    window.addEventListener("focus",()=>refresh().catch(()=>{}));
})();
