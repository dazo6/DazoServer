const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
(async()=>{
 const calls=[],user={userId:"reader",isSuperAdmin:false,permissions:["read:d:/test","read:d:/test/*"]};
 const context={
  Vue:{reactive:x=>x},ElementPlus:{ElMessage:{error:msg=>{throw new Error(msg);}}},
  location:{href:"http://localhost/dashboard/",origin:"http://localhost",replace:()=>{}},
  URL,Headers,Request,Date,document:{querySelector:()=>null},
  window:{fetch:async(url,options={})=>{calls.push({url,options});return {ok:true,status:200,json:async()=>({result:"SUCCESS",data:url==="/api/auth/me"?user:url==="/api/auth/csrf"?"csrf-test":true})};},addEventListener:()=>{}}
 };
 vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,"../../main/resources/static/dashboard/auth.js"),"utf8"),context);
 const auth=context.window.DazoAuth;await auth.ready;
 assert.equal(auth.can("read","D:\\test\\nested\\image.png"),true);
 assert.equal(auth.can("read","D:/test2/image.png"),false);
 assert.equal(auth.can("read","C:/test/image.png"),false);
 assert.equal(auth.can("update","D:/test/image.png"),false);
 assert.equal(auth.match("D:/test/*","D:/test"),false);
 await context.window.fetch("/api/users",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"});
 assert.equal(calls.at(-1).options.headers.get("X-CSRF-Token"),"csrf-test");
 user.permissions=["read:D:\\OneDrive\\MixedCase\\*"];
 assert.equal(auth.can("read","d:/onedrive/mixedcase/image.png"),true);
 assert.equal(user.permissions[0],"read:D:\\OneDrive\\MixedCase\\*");
 user.permissions=[];assert.equal(auth.can("read","D:/test/image.png"),false);
 user.isSuperAdmin=true;assert.equal(auth.can("delete","op:user.delete"),true);
 auth.state.user=null;assert.equal(auth.can("read","D:/test"),false);
 console.log("Frontend ACL matching, live state and CSRF checks passed");
})().catch(e=>{console.error(e);process.exit(1)});
