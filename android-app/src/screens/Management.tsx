import { useUI } from "../theme";
import { ServerFields } from "../servers";
import React, { useEffect, useState } from "react";
import { Alert, Linking, Pressable, Switch, View } from "react-native";
import {
  allowed,
  bytesLabel,
  dateLabel,
  memory,
  Native,
  query,
  Row,
} from "../core";
import { useApp, useDebounced, useRemote, useSaved, useScreenActive } from "../context";
import { UpdateSection } from "../updates";
import {
  Badge,
  Button,
  Card,
  confirm,
  Divider,
  Empty,
  Icon,
  IconButton,
  Input,
  Media,
  Menu,
  Options,
  Page,
  Pager,
  SearchBar,
  Section,
  SettingRow,
  Sheet,
  Txt,
} from "../ui";

const NL = String.fromCharCode(10);
export function Users() {
  const { C, S } = useUI();
  const { request, me, report } = useApp(),
    [keyword, setKeyword] = useSaved("users:keyword", ""),
    [page, setPage] = useSaved("users:page", 1),
    debounced = useDebounced(keyword),
    [menu, setMenu] = useState<Row | null>(null),
    [edit, setEdit] = useState<Row | null | undefined>(undefined),
    [password, setPassword] = useState<string | null>(null);
  const path = query("/api/users", { page, size: 20, keyword: debounced }),
    remote = useRemote<Row>(() => request("GET", path), [path]),
    caps = menu?.capabilities || {};
  const rows: Row[] = [
    ...(remote.data?.superAdmin ? [remote.data.superAdmin] : []),
    ...(remote.data?.records || []),
  ];
  const action = async (method: string, url: string, body?: Row) => {
    try {
      await request(method, url, body);
      await remote.reload();
    } catch (e) {
      report(e);
    }
  };
  return (
    <>
      <Page
        key={page}
        scrollKey="users"
        loading={remote.loading}
        refreshing={remote.refreshing}
        onRefresh={remote.refresh}
      >
        <SearchBar
          value={keyword}
          onChange={(v) => {
            setKeyword(v);
            setPage(1);
          }}
          placeholder="搜索用户 ID 或名称"
        />
        <Section
          title="用户"
          detail={`${remote.data?.total ?? "—"} 位普通用户`}
          action={
            allowed(me, "update", "op:user.create") ? (
              <IconButton
                name="plus"
                label="新增用户"
                filled
                onPress={() => setEdit(null)}
              />
            ) : undefined
          }
        />
        {remote.loading && !remote.data ? (
          <Empty loading />
        ) : (
          rows.map((a) => (
            <Card
              key={a.userId}
              onPress={() =>
                Alert.alert(
                  a.username,
                  (a.permissions || []).join(NL) || "暂无权限",
                )
              }
            >
              <View style={[S.row, { gap: 12 }]}>
                <Media
                  avatar
                  path={query("/api/users/avatar", { userId: a.userId })}
                  style={{ width: 44, height: 44, borderRadius: 14 }}
                />
                <View style={{ flex: 1 }}>
                  <Txt size={17} bold>
                    {a.username}
                  </Txt>
                  <Txt muted size={12}>
                    {a.userId}
                  </Txt>
                </View>
                {Object.values(a.capabilities || {}).some(Boolean) && (
                  <IconButton
                    name="more"
                    label="用户选项"
                    onPress={() => setMenu(a)}
                  />
                )}
              </View>
              <View style={[S.row, { marginTop: 12, gap: 8 }]}>
                <Badge
                  label={a.enabled ? "已启用" : "已停用"}
                  active={!!a.enabled}
                />
                {a.isSuperAdmin ? (
                  <Badge label="超级管理员" />
                ) : (
                  <Txt size={11} muted>
                    {(a.permissions || []).length} 项权限
                  </Txt>
                )}
              </View>
            </Card>
          ))
        )}
        <Pager
          page={page}
          size={20}
          total={remote.data?.total || 0}
          onChange={(p) => {
            memory.set("scroll:users", 0);
            setPage(p);
          }}
        />
      </Page>
      {menu && (
        <Menu
          title={menu.username}
          onClose={() => setMenu(null)}
          items={[
            ...(caps.canUpdate || caps.canPermissions
              ? [
                  {
                    label: "编辑用户",
                    icon: "edit",
                    action: () => setEdit(menu),
                  },
                ]
              : []),
            ...(caps.canStatus
              ? [
                  {
                    label: menu.enabled ? "停用用户" : "启用用户",
                    icon: "artist",
                    action: () =>
                      confirm(
                        menu.enabled ? "停用该用户？" : "启用该用户？",
                        () =>
                          void action(
                            "PUT",
                            `/api/users/${encodeURIComponent(menu.userId)}/enabled`,
                            { enabled: !menu.enabled },
                          ),
                      ),
                  },
                ]
              : []),
            ...(caps.canPassword
              ? [
                  {
                    label: "重置密码",
                    icon: "lock",
                    action: () => setPassword(menu.userId),
                  },
                ]
              : []),
            ...(caps.canSessions
              ? [
                  {
                    label: "退出所有会话",
                    icon: "external",
                    action: () =>
                      confirm(
                        "使该用户所有会话失效？",
                        () =>
                          void action(
                            "POST",
                            `/api/users/${encodeURIComponent(menu.userId)}/logout-all`,
                          ),
                      ),
                  },
                ]
              : []),
            ...(caps.canDelete
              ? [
                  {
                    label: "删除用户",
                    icon: "trash",
                    danger: true,
                    action: () =>
                      confirm(
                        `删除用户 ${menu.userId}？`,
                        () =>
                          void action(
                            "DELETE",
                            `/api/users/${encodeURIComponent(menu.userId)}`,
                          ),
                      ),
                  },
                ]
              : []),
          ]}
        />
      )}{" "}
      {edit !== undefined && (
        <UserEditor
          value={edit}
          close={() => setEdit(undefined)}
          saved={() => {
            setEdit(undefined);
            void remote.reload();
          }}
        />
      )}
      {password && (
        <PasswordEditor id={password} close={() => setPassword(null)} />
      )}
    </>
  );
}
function UserEditor({
  value,
  close,
  saved,
}: {
  value: Row | null;
  close(): void;
  saved(): void;
}) {
  const { C, S } = useUI();
  const { request, report } = useApp(),
    [id, setId] = useState(value?.userId || ""),
    [name, setName] = useState(value?.username || ""),
    [password, setPassword] = useState(""),
    [permissions, setPermissions] = useState(
      (value?.permissions || []).join(NL),
    ),
    [enabled, setEnabled] = useState(true),
    [busy, setBusy] = useState(false);
  const canName = !value || value.capabilities?.canUpdate,
    canPermissions = !value || value.capabilities?.canPermissions;
  useEffect(() => {
    if (!value)
      void request<Row>("GET", "/api/acl/defaults")
        .then((x) => setPermissions((x.resolvedPermissions || []).join(NL)))
        .catch(report);
  }, []);
  const save = async () => {
    if (
      !id.trim() ||
      (!value && (password.length < 8 || password.length > 128))
    ) {
      Alert.alert("请检查输入", "用户 ID 不能为空，密码须为 8～128 个字符。");
      return;
    }
    setBusy(true);
    const write = async () => {
      try {
        if (!value) {
          await request("POST", "/api/users", {
            userId: id.trim(),
            username: name.trim(),
            password,
            enabled,
            permissionText: permissions.trim(),
          });
        } else {
          const base = "/api/users/" + encodeURIComponent(id);
          if (canName) await request("PATCH", base, { username: name.trim() });
          if (canPermissions)
            await request("PUT", base + "/permissions", {
              permissionText: permissions.trim(),
            });
        }
        saved();
      } catch (e) {
        report(e);
      } finally {
        setBusy(false);
      }
    };
    try {
      if (canPermissions) {
        const result = await request<Row>("POST", "/api/acl/validate", {
          permissionText: permissions.trim(),
          ...(value ? { targetUserId: id } : {}),
        });
        if (result.willLoseManagement) {
          Alert.alert("保存后将失去管理权限", "是否继续保存？", [
            { text: "取消", style: "cancel", onPress: () => setBusy(false) },
            { text: "继续保存", onPress: () => void write() },
          ]);
          return;
        }
      }
      await write();
    } catch (e) {
      setBusy(false);
      report(e);
    }
  };
  return (
    <Sheet visible title={value ? "编辑用户" : "新增用户"} onClose={close}>
      {!value ? (
        <Input label="用户 ID" value={id} onChange={setId} />
      ) : (
        <Txt muted size={13} style={{ marginBottom: 16 }}>
          {id}
        </Txt>
      )}
      {canName && <Input label="用户名" value={name} onChange={setName} />}{" "}
      {!value && (
        <>
          <Input label="密码" secure value={password} onChange={setPassword} />
          <View
            style={[
              S.row,
              { justifyContent: "space-between", marginBottom: 20 },
            ]}
          >
            <Txt>启用用户</Txt>
            <Switch
              value={enabled}
              onValueChange={setEnabled}
              thumbColor={enabled ? C.blue : "#c4ccd6"}
            />
          </View>
        </>
      )}
      {canPermissions && (
        <>
          <Input
            label="权限（每行一条）"
            multiline
            value={permissions}
            onChange={setPermissions}
          />
          <View style={[S.row, { gap: 10, marginBottom: 16 }]}>
            <Pressable
              style={{ padding: 10, borderRadius: 8, backgroundColor: C.pale }}
              onPress={() =>
                setPermissions(["read:*", "update:*", "delete:*"].join(NL))
              }
            >
              <Txt size={12} style={{ color: C.blue }}>
                管理员模板
              </Txt>
            </Pressable>
            <Pressable
              style={{ padding: 10, borderRadius: 8, backgroundColor: C.pale }}
              onPress={() =>
                void request<Row>("GET", "/api/acl/defaults")
                  .then((x) =>
                    setPermissions((x.resolvedPermissions || []).join(NL)),
                  )
                  .catch(report)
              }
            >
              <Txt size={12} style={{ color: C.blue }}>
                普通用户模板
              </Txt>
            </Pressable>
          </View>
        </>
      )}
      <Button title="保存" busy={busy} onPress={() => void save()} />
    </Sheet>
  );
}
function PasswordEditor({
  id,
  self = false,
  close,
}: {
  id: string;
  self?: boolean;
  close(): void;
}) {
  const { request, report, toast } = useApp(),
    [old, setOld] = useState(""),
    [fresh, setFresh] = useState(""),
    [busy, setBusy] = useState(false);
  const save = async () => {
    if (fresh.length < 8 || fresh.length > 128) {
      Alert.alert("密码须为 8～128 个字符");
      return;
    }
    setBusy(true);
    try {
      await request(
        "PUT",
        self
          ? "/api/auth/me/password"
          : `/api/users/${encodeURIComponent(id)}/password`,
        { ...(self ? { oldPassword: old } : {}), newPassword: fresh },
      );
      toast("密码已更新");
      close();
    } catch (e) {
      report(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet visible title={self ? "修改密码" : "重置密码"} onClose={close}>
      {self && <Input label="旧密码" secure value={old} onChange={setOld} />}
      <Input label="新密码" secure value={fresh} onChange={setFresh} />
      <Button title="保存密码" busy={busy} onPress={() => void save()} />
    </Sheet>
  );
}

export function Account() {
  const { C, S } = useUI();
  const {
      me,
      host,
      setMe,
      request,
      report,
      logout,
      avatarVersion,
      avatarChanged,
      toast,
    } = useApp(),
    [refreshing, setRefreshing] = useState(false),
    [edit, setEdit] = useState(false),
    [name, setName] = useState(me.username || ""),
    [password, setPassword] = useState(false),
    [busy, setBusy] = useState(false);
  const refresh = async () => {
    setRefreshing(true);
    try {
      setMe(await request("GET", "/api/auth/me"));
    } catch (e) {
      report(e);
    } finally {
      setRefreshing(false);
    }
  };
  const avatar = async () => {
    try {
      await Native.pickAvatar();
      avatarChanged();
      toast("头像已保存");
    } catch (e) {
      report(e);
    }
  };
  const save = async () => {
    setBusy(true);
    try {
      await request("PATCH", "/api/auth/me", { username: name.trim() });
      setMe(await request("GET", "/api/auth/me"));
      setEdit(false);
    } catch (e) {
      report(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <Page refreshing={refreshing} onRefresh={() => void refresh()}>
        <View
          style={{
            alignItems: "center",
            paddingTop: 16,
            paddingBottom: 30,
            gap: 10,
          }}
        >
          <Pressable
            accessibilityLabel="更换头像"
            onPress={() => void avatar()}
          >
            <Media
              avatar
              path={query("/api/users/avatar", {
                userId: me.userId,
                t: avatarVersion,
              })}
              style={{ width: 86, height: 86, borderRadius: 28 }}
            />
            <View
              style={{
                position: "absolute",
                bottom: -3,
                right: -5,
                backgroundColor: C.surface,
                borderRadius: 12,
                padding: 3,
                borderWidth: 1,
                borderColor: C.line,
              }}
            >
              <Icon name="edit" size={22} />
            </View>
          </Pressable>
          <Txt size={22} bold>
            {me.username}
          </Txt>
          <Txt size={12} muted>
            {me.userId}
          </Txt>
        </View>
        <Card style={{ paddingVertical: 0 }}>
          <SettingRow
            icon="artist"
            title="用户名"
            value={me.username}
            onPress={() => {
              setName(me.username || "");
              setEdit(true);
            }}
          />
          {!me.isSuperAdmin && (
            <>
              <Divider />
              <SettingRow
                icon="lock"
                title="修改密码"
                onPress={() => setPassword(true)}
              />
            </>
          )}
          <Divider />
          <SettingRow
            icon="shield"
            title="当前权限"
            value={
              me.isSuperAdmin
                ? "超级管理员"
                : `${(me.permissions || []).length} 项`
            }
            onPress={() =>
              Alert.alert(
                "当前权限",
                (me.permissions || []).join(NL) || "暂无权限",
              )
            }
          />
        </Card>
        <Card style={{ paddingVertical: 0 }}>
          <SettingRow
            icon="external"
            title="服务器"
            value={host.replace(/^https?:\/\//, "")}
            onPress={() => Alert.alert("服务器", host)}
          />
        </Card>
        <View style={{ marginTop: 18 }}>
          <Button
            title="退出登录"
            quiet
            danger
            onPress={() => confirm("退出当前账户？", () => void logout())}
          />
        </View>
      </Page>
      <Sheet visible={edit} title="修改用户名" onClose={() => setEdit(false)}>
        <Input
          label="用户名"
          placeholder="留空使用用户 ID"
          value={name}
          onChange={setName}
        />
        <Button title="保存" busy={busy} onPress={() => void save()} />
      </Sheet>
      {password && (
        <PasswordEditor id={me.userId} self close={() => setPassword(false)} />
      )}
    </>
  );
}
export function Settings() {
  const { C, S, mode, setMode } = useUI();
  const { report, toast, originalImages, setOriginalImages } = useApp(),
    [size, setSize] = useState(0),
    [refreshing, setRefreshing] = useState(false),
    [busy, setBusy] = useState(false),
    [serversOpen, setServersOpen] = useState(false);
  const refresh = async () => {
    setRefreshing(true);
    try {
      setSize(await Native.cacheSize());
    } catch (e) {
      report(e);
    } finally {
      setRefreshing(false);
    }
  };
  useEffect(() => {
    void refresh();
  }, []);
  const clear = async () => {
    setBusy(true);
    try {
      await Native.clearCache();
      await refresh();
      toast("缓存已清理");
    } catch (e) {
      report(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Page
      scrollKey="settings"
      refreshing={refreshing}
      onRefresh={() => void refresh()}
    >
      <Section title="外观" />
      <Card>
        <Options
          label="主题模式"
          values={[
            { label: "跟随系统", value: "system" },
            { label: "深色", value: "dark" },
            { label: "浅色", value: "light" },
          ]}
          value={mode}
          onChange={setMode}
        />
      </Card>
      <Card style={{ paddingVertical: 0 }}>
        <SettingRow
          icon="external"
          title="服务器地址"
          value="主地址与备用地址"
          onPress={() => setServersOpen(true)}
        />
      </Card>
      <UpdateSection />
      <Section title="图片浏览" />
      <Card style={{ paddingVertical: 0 }}>
        <View style={[S.row, { minHeight: 64, gap: 12 }]}>
          <Icon name="gallery" />
          <View style={{ flex: 1, paddingVertical: 10 }}>
            <Txt>直接显示原图</Txt>
            <Txt size={12} muted>
              视频和文件夹仍使用缩略图
            </Txt>
          </View>
          <Switch
            accessibilityLabel="直接显示原图"
            value={originalImages}
            onValueChange={setOriginalImages}
            trackColor={{ false: "#cbd5e1", true: C.cyan }}
            thumbColor={originalImages ? C.blue : "#f8fafc"}
          />
        </View>
      </Card>
      <View style={{ height: 12 }} />
      <Section title="存储与缓存" />
      <Card>
        <View style={[S.row, { gap: 12 }]}>
          <Icon name="storage" size={40} />
          <View>
            <Txt size={28} bold>
              {bytesLabel(size)}
            </Txt>
            <Txt size={12} muted>
              上限 256 MiB
            </Txt>
          </View>
        </View>
        <View
          style={{
            height: 8,
            borderRadius: 4,
            backgroundColor: C.bg,
            marginTop: 22,
            marginBottom: 16,
            overflow: "hidden",
          }}
        >
          <View
            style={{
              height: 8,
              width: `${Math.min(100, (size / 1024 / 1024 / 256) * 100)}%`,
              backgroundColor: C.blue,
            }}
          />
        </View>
        <Txt size={13} muted>
          优先清理最久未使用的媒体；超过 30 天未使用的缓存会自动删除。
        </Txt>
      </Card>
      <View style={{ marginTop: 10 }}>
        <Button
          title="清理媒体缓存"
          quiet
          busy={busy}
          onPress={() =>
            confirm(
              "清理本地媒体缓存？",
              () => void clear(),
              "服务器中的原文件会保留。",
            )
          }
        />
      </View>
      {serversOpen && <ServerEditor close={() => setServersOpen(false)} />}
    </Page>
  );
}
function ServerEditor({ close }: { close(): void }) {
  const { host, backupUrls, setBackupUrls, report, toast } = useApp();
  const [draft, setDraft] = useState(backupUrls),
    [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await setBackupUrls(draft.filter((value) => value.trim()));
      toast("备用地址已保存");
      close();
    } catch (error) {
      report(error);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet title="服务器地址" visible onClose={close}>
      <ServerFields host={host} backups={draft} setBackups={setDraft} />
      <Txt muted size={12} style={{ marginBottom: 16 }}>
        这些地址连接同一个服务，共用当前登录。主地址无法连接时自动切换。
      </Txt>
      <Button title="保存" busy={busy} onPress={() => void save()} />
    </Sheet>
  );
}
export function More() {
  const { C, S } = useUI();
  const { me, host, navigate, setMe, request, avatarVersion, report } =
      useApp(),
    [refreshing, setRefreshing] = useState(false);
  const refresh = async () => {
    setRefreshing(true);
    try {
      setMe(await request("GET", "/api/auth/me"));
    } catch (e) {
      report(e);
    } finally {
      setRefreshing(false);
    }
  };
  return (
    <Page refreshing={refreshing} onRefresh={() => void refresh()}>
      <Card onPress={() => navigate({ name: "account" })}>
        <View style={[S.row, { gap: 14 }]}>
          <Media
            avatar
            path={query("/api/users/avatar", {
              userId: me.userId,
              t: avatarVersion,
            })}
            style={{ width: 56, height: 56, borderRadius: 18 }}
          />
          <View style={{ flex: 1 }}>
            <Txt size={19} bold>
              {me.username}
            </Txt>
            <Txt size={12} muted>
              {me.userId}
            </Txt>
          </View>
          <Icon name="next" size={22} color={C.muted} />
        </View>
      </Card>
      <Txt
        size={12}
        muted
        bold
        style={{ marginTop: 10, marginBottom: 8, marginLeft: 4 }}
      >
        管理
      </Txt>
      <Card style={{ paddingVertical: 0 }}>
        {allowed(me, "read", "*") && (
          <>
            <SettingRow
              icon="history"
              title="历史爬取记录"
              onPress={() => navigate({ name: "history" })}
            />
            <Divider />
          </>
        )}
        {me.isSuperAdmin && (
          <>
            <SettingRow
              icon="task"
              title="定时任务管理"
              onPress={() => navigate({ name: "tasks" })}
            />
            <Divider />
          </>
        )}
        {allowed(me, "read", "op:user.list") && (
          <SettingRow
            icon="artist"
            title="用户管理"
            onPress={() => navigate({ name: "users" })}
          />
        )}
      </Card>
      <Txt
        size={12}
        muted
        bold
        style={{ marginTop: 10, marginBottom: 8, marginLeft: 4 }}
      >
        应用
      </Txt>
      <Card style={{ paddingVertical: 0 }}>
        <SettingRow
          icon="settings"
          title="设置"
          onPress={() => navigate({ name: "storage" })}
        />
        <Divider />
        <SettingRow
          icon="external"
          title="当前服务器"
          value={host.replace(/^https?:\/\//, "")}
          onPress={() => Alert.alert("当前服务器", host)}
        />
      </Card>
    </Page>
  );
}

type HistoryFilter = {
  status: string;
  start: string;
  end: string;
  size: number;
  page: number;
};
export function History() {
  const { C, S } = useUI();
  const { request, report } = useApp(),
    [keyword, setKeyword] = useSaved("history:keyword", ""),
    debounced = useDebounced(keyword);
  const [filters, setFilters] = useSaved<HistoryFilter>("history:filters", {
      status: "全部",
      start: "",
      end: "",
      size: 20,
      page: 1,
    }),
    [draft, setDraft] = useState(filters),
    [filterOpen, setFilterOpen] = useState(false);
  const path = query("/api/crawler-requests", {
    page: filters.page,
    pageSize: filters.size,
    keyword: debounced,
    done: filters.status === "全部" ? undefined : filters.status === "已完成",
    start: filters.start
      ? filters.start.length > 10
        ? filters.start
        : filters.start + " 00:00:00"
      : undefined,
    end: filters.end
      ? filters.end.length > 10
        ? filters.end
        : filters.end + " 23:59:59"
      : undefined,
  });
  const remote = useRemote<Row>(() => request("GET", path), [path]);
  const rows: Row[] = remote.data?.records || [];
  const date = async (which: "start" | "end") => {
    try {
      const value = await Native.pickDateTime(draft[which], which === "end");
      setDraft({ ...draft, [which]: value });
    } catch (e) {
      report(e);
    }
  };
  return (
    <>
      <Page
        key={filters.page}
        scrollKey="history"
        loading={remote.loading}
        refreshing={remote.refreshing}
        onRefresh={remote.refresh}
      >
        <SearchBar
          value={keyword}
          onChange={(v) => {
            setKeyword(v);
            setFilters({ ...filters, page: 1 });
          }}
          placeholder="搜索 URL"
          onFilter={() => {
            setDraft(filters);
            setFilterOpen(true);
          }}
        />
        <Section
          title={`${remote.data?.total ?? "—"} 条记录`}
          detail={`${filters.status} · ${filters.start || filters.end ? "已选时间范围" : "所有时间"}`}
        />
        {remote.loading && !remote.data ? (
          <Empty loading />
        ) : !rows.length ? (
          <Empty title="没有匹配的记录" />
        ) : (
          rows.map((a) => (
            <Card
              key={a.id}
              onPress={() => void Linking.openURL(a.url).catch(report)}
            >
              <View style={[S.row, { marginBottom: 10 }]}>
                <Txt bold style={{ flex: 1 }}>
                  #{a.id}
                </Txt>
                <Badge
                  label={a.isDone ? "已完成" : "未完成"}
                  active={!!a.isDone}
                />
              </View>
              <Txt
                size={14}
                lines={2}
                style={{ color: C.blue, marginBottom: 12 }}
              >
                {a.url}
              </Txt>
              <View style={S.row}>
                <Txt size={12} muted style={{ flex: 1 }}>
                  画师 {a.artistId}
                </Txt>
                <Txt
                  size={12}
                  style={{ color: a.errorCount ? C.red : C.muted }}
                >
                  错误 {a.errorCount || 0}
                </Txt>
              </View>
              <Txt size={11} muted style={{ marginTop: 6 }}>
                {dateLabel(a.gmtCreate)}
              </Txt>
            </Card>
          ))
        )}
        <Pager
          page={filters.page}
          total={remote.data?.total || 0}
          size={filters.size}
          onChange={(page) => {
            memory.set("scroll:history", 0);
            setFilters({ ...filters, page });
          }}
        />
      </Page>
      <Sheet
        visible={filterOpen}
        title="筛选记录"
        onClose={() => setFilterOpen(false)}
      >
        <Options
          label="抓取状态"
          values={["全部", "已完成", "未完成"].map((v) => ({
            label: v,
            value: v,
          }))}
          value={draft.status}
          onChange={(status) => setDraft({ ...draft, status })}
        />
        <Txt size={13} muted>
          时间范围
        </Txt>
        <SettingRow
          icon="task"
          title="开始时间"
          value={draft.start || "不限"}
          onPress={() => void date("start")}
        />
        <Divider />
        <SettingRow
          icon="task"
          title="结束时间"
          value={draft.end || "不限"}
          onPress={() => void date("end")}
        />
        <View style={{ height: 16 }} />
        <Options
          label="每页显示"
          values={[10, 20, 50, 100].map((v) => ({
            label: String(v),
            value: v,
          }))}
          value={draft.size}
          onChange={(size) => setDraft({ ...draft, size })}
        />
        <Button
          title="应用筛选"
          onPress={() => {
            if (draft.start && draft.end && draft.start > draft.end) {
              Alert.alert("日期范围无效", "结束日期应晚于开始日期");
              return;
            }
            setFilters({ ...draft, page: 1 });
            memory.set("scroll:history", 0);
            setFilterOpen(false);
          }}
        />
        <Button
          title="重置"
          quiet
          onPress={() => {
            setKeyword("");
            setFilters({
              status: "全部",
              start: "",
              end: "",
              size: 20,
              page: 1,
            });
            setFilterOpen(false);
          }}
        />
      </Sheet>
    </>
  );
}

export function Tasks() {
  const { C, S } = useUI();
  const active = useScreenActive();
  const { request, report } = useApp(),
    remote = useRemote<Row[]>(() => request("GET", "/api/scheduled-tasks"), []),
    [detail, setDetail] = useState<Row | null>(null),
    [busy, setBusy] = useState<string | null>(null);
  const rows = remote.data || [],
    running = rows.some((a) => a.running);
  useEffect(() => {
    if (!active || !running) return;
    const timer = setTimeout(() => void remote.reload(), 3000);
    return () => clearTimeout(timer);
  }, [remote.data, running, active]);
  const run = async (a: Row) => {
    setBusy(a.id);
    try {
      await request(
        "POST",
        `/api/scheduled-tasks/${encodeURIComponent(a.id)}/run`,
      );
      await remote.reload();
    } catch (e) {
      report(e);
    } finally {
      setBusy(null);
    }
  };
  return (
    <>
      <Page
        scrollKey="tasks"
        loading={remote.loading}
        refreshing={remote.refreshing}
        onRefresh={remote.refresh}
      >
        <Section
          title="任务"
          detail={`${rows.filter((a) => a.running).length} 个运行中`}
        />
        {remote.loading && !remote.data ? (
          <Empty loading />
        ) : !rows.length ? (
          <Empty title="暂无定时任务" />
        ) : (
          rows.map((a) => (
            <Card key={a.id} onPress={() => setDetail(a)}>
              <View style={[S.row, { gap: 12 }]}>
                <View style={{ flex: 1, gap: 8 }}>
                  <Txt size={17} bold>
                    {a.name}
                  </Txt>
                  <Badge
                    label={a.running ? "运行中" : "空闲"}
                    active={!!a.running}
                  />
                </View>
                {a.manualEnabled && !a.running && (
                  <IconButton
                    name="play"
                    label="运行任务"
                    filled
                    disabled={busy === a.id}
                    onPress={() =>
                      confirm(`运行“${a.name}”？`, () => void run(a))
                    }
                  />
                )}
              </View>
              <View style={{ marginTop: 16 }}>
                <Txt size={13} muted lines={2}>
                  {a.lastResult || "尚未运行"}
                </Txt>
                <Txt size={11} muted style={{ marginTop: 6 }}>
                  {dateLabel(a.finishedAt)}
                </Txt>
              </View>
            </Card>
          ))
        )}
      </Page>
      {detail && (
        <Sheet visible title={detail.name} onClose={() => setDetail(null)}>
          <Txt muted size={12}>
            {detail.beanName}
          </Txt>
          <View style={{ height: 18 }} />
          <Txt bold>调度规则</Txt>
          {(detail.rules || []).map((rule: string, i: number) => (
            <Txt key={i} size={13} muted style={{ marginTop: 6 }}>
              {rule}
            </Txt>
          ))}
          <View style={{ height: 20 }} />
          {[
            ["最近触发", detail.lastSource || "—"],
            ["开始时间", dateLabel(detail.startedAt)],
            ["结束时间", dateLabel(detail.finishedAt)],
            ["耗时", (detail.durationMs || 0) + " ms"],
          ].map(([label, value]) => (
            <Txt key={label} size={13}>
              {label}　{value}
            </Txt>
          ))}
          <View style={{ height: 20 }} />
          <Txt size={13} muted>
            {detail.lastResult || "暂无运行结果"}
          </Txt>
        </Sheet>
      )}
    </>
  );
}
