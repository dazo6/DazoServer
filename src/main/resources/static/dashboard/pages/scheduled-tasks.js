(() => {
    "use strict";
    const {ref,onMounted,onUnmounted}=Vue;
    const auth=window.DazoAuth;
    window.DazoAdminPages=window.DazoAdminPages || {};
    window.DazoAdminPages.ScheduledTasks={
        template: `<div class="scheduled-task-management">
          <div class="card-heading"><h2>定时任务管理</h2><el-button @click="refresh" :loading="loading">刷新</el-button></div>
          <p class="scheduled-task-note">状态每 3 秒自动刷新。手动运行期间跳过重复的定时触发。</p>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
          <el-empty v-if="!loading && !tasks.length && !error" description="暂无定时任务"/>
          <div class="scheduled-task-grid" v-loading="loading && !tasks.length">
            <el-card v-for="task in tasks" :key="task.id" shadow="never" class="scheduled-task-card">
              <template #header><div class="card-heading"><h3>{{task.name}}</h3><el-tag :type="task.running ? 'warning' : 'info'">{{task.running ? '运行中' : '空闲'}}</el-tag></div></template>
              <p class="scheduled-task-bean">{{task.beanName}}</p>
              <div class="scheduled-task-rules"><div v-for="rule in task.rules" :key="rule">{{rule}}</div></div>
              <dl class="scheduled-task-details">
                <dt>最近触发</dt><dd>{{task.lastSource === 'manual' ? '手动' : task.lastSource === 'scheduled' ? '定时' : '—'}}</dd>
                <dt>开始时间</dt><dd>{{time(task.startedAt)}}</dd>
                <dt>结束时间</dt><dd>{{time(task.finishedAt)}}</dd>
                <dt>耗时</dt><dd>{{task.durationMs == null ? '—' : (task.durationMs / 1000).toFixed(1) + ' 秒'}}</dd>
                <dt>最近结果</dt><dd><el-tag v-if="task.lastResult" :type="task.lastResult === 'SUCCESS' ? 'success' : 'danger'">{{task.lastResult === 'SUCCESS' ? '成功' : '失败'}}</el-tag><span v-else>—</span></dd>
              </dl>
              <el-button type="primary" :disabled="task.running || submitting.includes(task.id) || !task.manualEnabled" :title="task.running ? '任务正在运行' : !task.manualEnabled ? '测试环境禁用运行' : '异步执行任务'" @click="run(task)">{{task.running || submitting.includes(task.id) ? '运行中' : '手动运行'}}</el-button>
            </el-card>
          </div>
        </div>`,
        setup() {
            const tasks=ref([]),loading=ref(false),error=ref(""),submitting=ref([]);
            let active=false,timer=null,controller=null;
            async function refresh() {
                if (!active || loading.value) return;
                clearTimeout(timer); loading.value=true;
                controller=new AbortController();
                try {
                    const rows=await auth.request("/api/scheduled-tasks",{signal:controller.signal});
                    if (active) {tasks.value=rows; error.value="";}
                } catch(e) { if (active && e.name!=="AbortError") error.value=e.message; }
                finally { loading.value=false; if(active) timer=setTimeout(refresh,3000); }
            }
            async function run(task) {
                if (task.running || submitting.value.includes(task.id)) return;
                submitting.value=[...submitting.value,task.id];
                try {
                    await auth.request("/api/scheduled-tasks/"+encodeURIComponent(task.id)+"/run",{method:"POST"});
                    if(active) {task.running=true; ElementPlus.ElMessage.success("任务已提交");}
                } catch(e) {if(active) ElementPlus.ElMessage.error(e.message);}
                finally {submitting.value=submitting.value.filter(id=>id!==task.id); if(active) refresh();}
            }
            function time(value) {return value == null ? "—" : new Date(value).toLocaleString();}
            onMounted(()=>{active=true;refresh();});
            onUnmounted(()=>{active=false;clearTimeout(timer);if(controller) controller.abort();});
            return {tasks,loading,error,submitting,refresh,run,time};
        }
    };
})();
