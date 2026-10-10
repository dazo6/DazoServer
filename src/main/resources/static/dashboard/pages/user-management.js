(() => {
    "use strict";
    const {ref,reactive,onMounted,computed}=Vue;
    const auth=window.DazoAuth;
    window.DazoAdminPages=window.DazoAdminPages || {};
    window.DazoAdminPages.UserManagement={
        template: `<div class="user-management">
        <el-card shadow="never"><template #header><div class="card-heading"><h2>用户管理</h2>
          <el-button type="primary" :disabled="!can('update','op:user.create')" @click="create">新增用户</el-button></div></template>
          <div class="user-search"><el-input v-model="keyword" clearable placeholder="搜索用户 ID 或名称" @keyup.enter="load"/><el-button @click="load">查询</el-button></div>
          <el-table :data="displayRows" v-loading="loading" empty-text="没有可管理用户">
            <el-table-column label="头像" width="70"><template #default="{row}"><el-avatar :src="avatarUrl(row.userId)"/></template></el-table-column>
            <el-table-column prop="userId" label="用户 ID" min-width="130"/>
            <el-table-column prop="username" label="用户名" min-width="140"/>
            <el-table-column label="启用" width="90"><template #default="{row}">
              <el-switch :model-value="row.enabled" :disabled="!row.capabilities?.canStatus" @change="toggle(row,$event)"/>
            </template></el-table-column>
            <el-table-column label="权限" min-width="230"><template #default="{row}">
              <el-tag v-if="row.isSuperAdmin">超管</el-tag>
              <div v-else class="user-permission-list"><div v-for="point in row.permissions" :key="point">{{point}}</div><span v-if="!row.permissions?.length">无业务权限</span></div>
            </template></el-table-column>
            <el-table-column label="操作" min-width="280"><template #default="{row}">
              <el-button link :disabled="!row.capabilities?.canUpdate && !row.capabilities?.canPermissions" @click="edit(row)">编辑</el-button>
              <el-button link :disabled="!row.capabilities?.canPassword" @click="resetPassword(row)">重置密码</el-button>
              <el-button link :disabled="!row.capabilities?.canSessions" @click="logoutAll(row)">退出会话</el-button>
              <el-button link type="danger" :disabled="!row.capabilities?.canDelete" @click="remove(row)">删除</el-button>
            </template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total,prev,pager,next" @current-change="load"/>
        </el-card>
        <el-dialog class="user-editor-dialog" v-model="dialog" :title="editing ? '编辑用户' : '新增用户'" width="min(620px,95vw)">
          <el-form label-width="90px">
            <el-form-item label="用户 ID"><el-input v-model="form.userId" :disabled="editing" maxlength="64"/></el-form-item>
            <el-form-item label="用户名"><el-input v-model="form.username" :disabled="editing && !caps.canUpdate" placeholder="留空使用用户 ID" maxlength="64"/></el-form-item>
            <el-form-item v-if="!editing" label="密码"><el-input v-model="form.password" type="password" show-password placeholder="8～128字符"/></el-form-item>
            <el-form-item v-if="!editing" label="启用"><el-switch v-model="form.enabled"/></el-form-item>
            <el-form-item label="快捷模板">
              <el-button :disabled="editing && !caps.canPermissions" @click="template('admin')">管理员</el-button>
              <el-button :disabled="editing && !caps.canPermissions" @click="template('reader')">普通用户</el-button>
            </el-form-item>
            <el-form-item label="权限">
              <div class="permission-editor">
                <div v-for="(rule,index) in form.permissionRows" :key="rule.key" class="permission-row">
                  <el-select v-model="rule.type" :disabled="editing && !caps.canPermissions" aria-label="权限类型">
                    <el-option label="read" value="read"/><el-option label="update" value="update"/><el-option label="delete" value="delete"/>
                  </el-select>
                  <el-input v-model="rule.point" :disabled="editing && !caps.canPermissions" placeholder="权限点，如 D:/test/*" aria-label="权限点"/>
                  <el-button class="permission-remove" :disabled="editing && !caps.canPermissions" @click="removePermission(index)" aria-label="删除权限行" title="删除权限行">−</el-button>
                </div>
                <el-button class="permission-add" :disabled="editing && !caps.canPermissions" @click="addPermission" aria-label="添加权限行"><span aria-hidden="true">＋</span> 添加一行</el-button>
              </div>
            </el-form-item>
          </el-form>
          <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
        </el-dialog></div>`,
        setup() {
            const rows=ref([]),root=ref(null),total=ref(0),page=ref(1),keyword=ref(""),loading=ref(false),dialog=ref(false),saving=ref(false),editing=ref(false);
            const caps=ref({}),form=reactive({userId:"",username:"",password:"",enabled:true,permissionRows:[]});
            const displayRows=computed(()=>root.value?[root.value,...rows.value]:rows.value);
            async function load() {
                loading.value=true;
                try {
                    const d=await auth.request("/api/users?"+new URLSearchParams({page:page.value,size:20,keyword:keyword.value}));
                    rows.value=d.records;root.value=d.superAdmin;total.value=d.total;
                }catch(e){ElementPlus.ElMessage.error(e.message);}finally{loading.value=false;}
            }
            function avatarUrl(id){return "/api/users/avatar?userId="+encodeURIComponent(id)+"&t="+auth.state.avatarStamp;}
            let nextPermissionKey=0;
            function setPermissions(points){form.permissionRows=points.map(point=>{const colon=point.indexOf(":");return {key:++nextPermissionKey,type:point.slice(0,colon),point:point.slice(colon+1)};});}
            function addPermission(){form.permissionRows.push({key:++nextPermissionKey,type:"read",point:""});}
            function removePermission(index){form.permissionRows.splice(index,1);}
            function permissionText(){
                if(form.permissionRows.some(rule=>!rule.point.trim()))throw new Error("请填写每一行的权限点，或删除空行");
                return form.permissionRows.map(rule=>rule.type+":"+rule.point).join("\n");
            }
            function create(){editing.value=false;caps.value={};Object.assign(form,{userId:"",username:"",password:"",enabled:true});setPermissions(["read:default_path"]);dialog.value=true;}
            function edit(row){editing.value=true;caps.value=row.capabilities;Object.assign(form,{...row,password:"",permissionRows:[]});setPermissions(row.permissions||[]);dialog.value=true;}
            async function template(kind){
                if(form.permissionRows.length && !(form.permissionRows.length===1 && form.permissionRows[0].type==="read" && form.permissionRows[0].point==="default_path")) {
                    try{await ElementPlus.ElMessageBox.confirm("覆盖当前权限配置？","切换模板");}catch{return;}
                }
                if(kind==="admin") setPermissions(["read:*","update:*","delete:*"]);
                else {
                    const d=await auth.request("/api/acl/defaults");
                    setPermissions(d.resolvedPermissions);
                }
            }
            async function save(){
                saving.value=true;
                try {
                    const points=(!editing.value || caps.value.canPermissions)?permissionText():"";
                    if(!editing.value || caps.value.canPermissions) {
                        const b={permissionText:points};
                        if(editing.value)b.targetUserId=form.userId;
                        const d=await auth.request("/api/acl/validate",auth.json("POST",b));
                        if(d.willLoseManagement) {
                            try{await ElementPlus.ElMessageBox.confirm("保存后该用户与你权限相等，你将不能继续管理它。是否继续？","管理范围提示");}
                            catch{return;}
                        }
                    }
                    if(editing.value){
                        const base="/api/users/"+encodeURIComponent(form.userId);
                        if(caps.value.canUpdate) await auth.request(base,auth.json("PATCH",{username:form.username}));
                        if(caps.value.canPermissions) await auth.request(base+"/permissions",auth.json("PUT",{permissionText:points}));
                    }else await auth.request("/api/users",auth.json("POST",{userId:form.userId,username:form.username,password:form.password,enabled:form.enabled,permissionText:points}));
                    dialog.value=false;ElementPlus.ElMessage.success("已保存");await auth.refresh();await load();
                }catch(e){ElementPlus.ElMessage.error(e.message);}finally{saving.value=false;}
            }
            async function action(row,suffix,method,body){
                try{await auth.request("/api/users/"+encodeURIComponent(row.userId)+suffix,body===undefined?{method}:auth.json(method,body));await load();}
                catch(e){ElementPlus.ElMessage.error(e.message);}
            }
            async function toggle(row,on){await action(row,"/enabled","PUT",{enabled:on});}
            async function remove(row){
                try{await ElementPlus.ElMessageBox.confirm("删除 "+row.userId+"？已有会话会失效。","删除用户",{type:"warning"});}catch{return;}
                await action(row,"","DELETE");
            }
            async function resetPassword(row){
                try{
                    const {value}=await ElementPlus.ElMessageBox.prompt("请输入8～128字符的新密码，已有会话继续有效。","重置密码",{inputType:"password",inputValidator:v=>v?.length>=8&&v.length<=128 || "密码长度不正确"});
                    await action(row,"/password","PUT",{newPassword:value});
                }catch(e){if(e!=="cancel"&&e!=="close")ElementPlus.ElMessage.error(e.message || e);}
            }
            async function logoutAll(row){
                try{await ElementPlus.ElMessageBox.confirm("终止 "+row.userId+" 的所有登录会话？","退出会话");}catch{return;}
                await action(row,"/logout-all","POST");
            }
            onMounted(load);
            return {rows,root,total,page,keyword,loading,dialog,saving,editing,caps,form,displayRows,load,avatarUrl,create,edit,template,addPermission,removePermission,save,toggle,remove,resetPassword,logoutAll,can:auth.can};
        }
    };
    window.DazoAdminPages.MyAccount={
        template:`<el-card shadow="never" class="my-account"><template #header><div class="card-heading"><h2>我的账户</h2><el-button @click="logout">退出登录</el-button></div></template>
          <div class="account-identity">
            <button type="button" class="account-avatar-picker" @click="avatarInput.click()" aria-label="更换头像" title="更换头像"><el-avatar :size="48" :src="avatarUrl"/></button>
            <input ref="avatarInput" type="file" hidden accept="image/png,image/jpeg" @change="choose"/>
            <div class="account-identity-details">
              <el-input v-model="username" :disabled="nameSaving" maxlength="64" placeholder="留空使用用户 ID" aria-label="用户名" @change="saveName"/>
              <small>用户 ID：{{authState.user?.userId}}</small>
            </div>
          </div>
          <el-form label-width="100px" style="max-width:600px">
            <template v-if="!authState.user?.isSuperAdmin">
              <el-form-item label="旧密码"><el-input v-model="oldPassword" type="password" show-password/></el-form-item>
              <el-form-item label="新密码"><el-input v-model="newPassword" type="password" show-password placeholder="8～128字符"/></el-form-item>
              <el-form-item><el-button @click="savePassword">修改密码</el-button></el-form-item>
            </template>
            <el-form-item label="当前权限"><pre class="permission-preview">{{permissionText}}</pre></el-form-item>
          </el-form>
          <el-dialog v-model="cropVisible" title="裁剪头像" width="min(430px,95vw)" @closed="release">
            <p>拖动图片调整位置，滑动缩放。</p>
            <canvas ref="canvas" width="300" height="300" class="avatar-crop" @pointerdown="dragStart" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd"/>
            <el-slider v-model="zoom" :min="1" :max="4" :step="0.01" @input="draw"/>
            <template #footer><el-button @click="cropVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="upload">保存头像</el-button></template>
          </el-dialog></el-card>`,
        setup(){
            const avatarInput=ref(null),nameSaving=ref(false);
            const username=ref(""),oldPassword=ref(""),newPassword=ref(""),cropVisible=ref(false),canvas=ref(null),zoom=ref(1),saving=ref(false);
            const permissionText=computed(()=>(auth.state.user?.permissions || []).join("\n"));
            const avatarUrl=computed(()=>auth.state.user?"/api/users/avatar?userId="+encodeURIComponent(auth.state.user.userId)+"&t="+auth.state.avatarStamp:"/favicon.ico");
            let image=null,imageUrl="",offsetX=0,offsetY=0,drag=null;
            onMounted(async()=>{await auth.ready;username.value=auth.state.user.username;});
            async function choose(event){
                const file=event.target.files[0];event.target.value="";
                if(!file)return;
                if(file.size>2097152 || !["image/png","image/jpeg"].includes(file.type)){ElementPlus.ElMessage.error("请选择2MiB以内的PNG或JPEG");return;}
                release();imageUrl=URL.createObjectURL(file);image=new Image();
                image.onload=async()=>{zoom.value=1;offsetX=offsetY=0;cropVisible.value=true;await Vue.nextTick();draw();};
                image.onerror=()=>{release();ElementPlus.ElMessage.error("图片无效");};image.src=imageUrl;
            }
            function draw(){
                if(!canvas.value || !image)return;
                const g=canvas.value.getContext("2d"),scale=300/Math.min(image.width,image.height)*zoom.value;
                const w=image.width*scale,h=image.height*scale;
                offsetX=Math.max(-(w-300)/2,Math.min((w-300)/2,offsetX));
                offsetY=Math.max(-(h-300)/2,Math.min((h-300)/2,offsetY));
                g.clearRect(0,0,300,300);g.drawImage(image,(300-w)/2+offsetX,(300-h)/2+offsetY,w,h);
            }
            function dragStart(e){drag={x:e.clientX,y:e.clientY};e.target.setPointerCapture(e.pointerId);}
            function dragMove(e){if(!drag)return;const ratio=300/canvas.value.getBoundingClientRect().width;offsetX+=(e.clientX-drag.x)*ratio;offsetY+=(e.clientY-drag.y)*ratio;drag={x:e.clientX,y:e.clientY};draw();}
            function dragEnd(){drag=null;}
            function release(){if(imageUrl)URL.revokeObjectURL(imageUrl);imageUrl="";image=null;drag=null;}
            async function upload(){
                saving.value=true;
                try{
                    const output=document.createElement("canvas");output.width=output.height=256;output.getContext("2d").drawImage(canvas.value,0,0,256,256);
                    const blob=await new Promise(r=>output.toBlob(r,"image/png"));const form=new FormData();form.append("file",blob,"avatar.png");
                    await auth.request("/api/auth/me/avatar",{method:"POST",body:form});auth.refreshAvatar();cropVisible.value=false;ElementPlus.ElMessage.success("头像已保存");
                }catch(e){ElementPlus.ElMessage.error(e.message);}finally{saving.value=false;}
            }
            async function saveName(){
                if(nameSaving.value || username.value===auth.state.user.username)return;
                nameSaving.value=true;
                try{await auth.request("/api/auth/me",auth.json("PATCH",{username:username.value}));await auth.refresh();username.value=auth.state.user.username;ElementPlus.ElMessage.success("名称已保存");}
                catch(e){ElementPlus.ElMessage.error(e.message);}
                finally{nameSaving.value=false;}
            }
            async function savePassword(){try{await auth.request("/api/auth/me/password",auth.json("PUT",{oldPassword:oldPassword.value,newPassword:newPassword.value}));oldPassword.value=newPassword.value="";ElementPlus.ElMessage.success("密码已修改");}catch(e){ElementPlus.ElMessage.error(e.message);}}
            Vue.onBeforeUnmount(release);
            return {avatarInput,nameSaving,logout:auth.logout,permissionText,authState:auth.state,username,oldPassword,newPassword,cropVisible,canvas,zoom,saving,avatarUrl,choose,draw,dragStart,dragMove,dragEnd,release,upload,saveName,savePassword};
        }
    };
})();
