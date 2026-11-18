import { SavedList } from "../list";
import { useUI } from "../theme";
import React, { useState } from "react";
import {
  ActivityIndicator,
  RefreshControl,
  StyleSheet,
  Alert,
  Linking,
  Pressable,
  ScrollView,
  Switch,
  View,
} from "react-native";
import { allowed, dateLabel, updateAge, memory, query, Row } from "../core";
import {
  useApp,
  useClock,
  usePagedRemote,
  useDebounced,
  useRemote,
  useSaved,
} from "../context";
import {
  Badge,
  Button,
  Card,
  confirm,
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
  Sheet,
  Txt,
} from "../ui";

export function Home() {
  const { C, S } = useUI();
  const now = useClock();
  const { request, navigate, report } = useApp(),
    remote = useRemote<Row>(() => request("GET", "/api/dashboard"), []);
  const [keyword, setKeyword] = useSaved("home:keyword", ""),
    [count, setCount] = useSaved("home:visibleCount", 20),
    [headerHeight, setHeaderHeight] = useState(640);
  const data = remote.data,
    hours: Row[] = data?.hourlyCounts || [],
    statuses: Row[] = data?.statusCounts || [],
    artists: Row[] = data?.artistUpdates || [];
  const total = hours.reduce((n, a) => n + Number(a.count || 0), 0),
    done = statuses
      .filter((a) => a.done)
      .reduce((n, a) => n + Number(a.count || 0), 0),
    pending = statuses
      .filter((a) => !a.done)
      .reduce((n, a) => n + Number(a.count || 0), 0),
    percent = done + pending ? Math.round((done * 100) / (done + pending)) : 0;
  const updates = artists.filter((a) =>
    (a.name || "").toLowerCase().includes(keyword.toLowerCase()),
  );
  const hourDetails = async (hour: Row) => {
    try {
      const result = await request<Row>(
        "GET",
        query("/api/dashboard/hour-artists", { hour: hour.hour }),
      );
      Alert.alert(
        dateLabel(hour.hour),
        (result.artists || [])
          .map((a: Row) => `${a.name}　${a.count} 条`)
          .join("\n") +
          (result.unknownCount
            ? `\n未知画师　${result.unknownCount} 条`
            : "") || "这个时段暂无记录",
      );
    } catch (e) {
      report(e);
    }
  };
  return (
    <SavedList
      resetKey={keyword}
      scrollKey="home"
      ready={!!data && !remote.loading}
      expectedHeight={headerHeight + Math.min(count, updates.length) * 78 + 40}
      data={updates.slice(0, count)}
      keyExtractor={(a, i) => String(a.id || a.name || i)}
      contentContainerStyle={S.page}
      refreshControl={
        <RefreshControl
          refreshing={remote.refreshing}
          onRefresh={remote.refresh}
          colors={[C.blue]}
          progressBackgroundColor={C.surface}
        />
      }
      onEndReached={() => {
        if (data && count < updates.length)
          setCount(Math.min(updates.length, count + 20));
      }}
      onEndReachedThreshold={0.6}
      initialNumToRender={12}
      maxToRenderPerBatch={8}
      windowSize={7}
      removeClippedSubviews={false}
      getItemLayout={(_, index) => ({
        index,
        length: 78,
        offset: 16 + headerHeight + index * 78,
      })}
      ListHeaderComponent={
        <View
          onLayout={(event) => setHeaderHeight(event.nativeEvent.layout.height)}
        >
          {!data ? (
            <Empty loading={remote.loading} />
          ) : (
            <>
              {" "}
              <View style={[S.row, { gap: 12 }]}>
                <Card style={{ flex: 1 }}>
                  <Txt size={12} muted>
                    近 150 小时
                  </Txt>
                  <Txt size={29} bold style={{ marginTop: 6, color: C.blue }}>
                    {total.toLocaleString()}
                  </Txt>
                  <Txt size={12} muted>
                    抓取完成
                  </Txt>
                </Card>
                <Card style={{ flex: 1 }}>
                  <Txt size={12} muted>
                    完成比例
                  </Txt>
                  <Txt size={29} bold style={{ marginTop: 6 }}>
                    {percent}
                    <Txt size={16} muted>
                      %
                    </Txt>
                  </Txt>
                  <Txt size={12} muted>
                    {pending.toLocaleString()} 条待完成
                  </Txt>
                </Card>
              </View>
              <Card>
                <Section title="抓取趋势" detail="每小时完成数量" />
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentOffset={{
                    x: Math.max(0, hours.length * 18 - 320),
                    y: 0,
                  }}
                >
                  <View
                    style={{
                      flexDirection: "row",
                      alignItems: "flex-end",
                      height: 136,
                      gap: 5,
                      paddingTop: 10,
                    }}
                  >
                    {hours.map((hour, i) => (
                      <Pressable
                        key={i}
                        accessibilityLabel={
                          dateLabel(hour.hour) + "，" + hour.count + "条"
                        }
                        onPress={() => void hourDetails(hour)}
                        style={{
                          width: 13,
                          height: 125,
                          justifyContent: "flex-end",
                          alignItems: "center",
                        }}
                      >
                        <View
                          style={{
                            width: 9,
                            height: Math.max(
                              3,
                              (Number(hour.count || 0) /
                                Math.max(
                                  1,
                                  ...hours.map((x) => Number(x.count)),
                                )) *
                                100,
                            ),
                            backgroundColor:
                              i >= hours.length - 24 ? C.blue : "#addcea",
                            borderRadius: 4,
                          }}
                        />
                        {i % 6 === 0 ? (
                          <Txt
                            size={8}
                            muted
                            style={{
                              height: 19,
                              width: 35,
                              textAlign: "center",
                            }}
                          >
                            {String(hour.hour).slice(11, 16) || String(i)}
                          </Txt>
                        ) : (
                          <View style={{ height: 19 }} />
                        )}
                      </Pressable>
                    ))}
                  </View>
                </ScrollView>
              </Card>
              <Card>
                <Section title="请求状态" />
                <View
                  style={{
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: C.bg,
                    overflow: "hidden",
                    marginBottom: 14,
                  }}
                >
                  <View
                    style={{
                      width: `${percent}%`,
                      height: 8,
                      backgroundColor: C.blue,
                      borderRadius: 4,
                    }}
                  />
                </View>
                <View style={S.row}>
                  <Txt size={13} style={{ flex: 1, color: C.blue }}>
                    已完成　{done.toLocaleString()}
                  </Txt>
                  <Txt size={13} muted>
                    未完成　{pending.toLocaleString()}
                  </Txt>
                </View>
              </Card>
              <View style={{ height: 12 }} />
              <Section title="最近更新" detail={`${artists.length} 位画师`} />
              <SearchBar
                value={keyword}
                onChange={(v) => {
                  setKeyword(v);
                  setCount(20);
                  memory.set("scroll:home", 0);
                }}
                placeholder="搜索更新画师"
              />
            </>
          )}
        </View>
      }
      ListEmptyComponent={
        data && !updates.length ? <Empty title="没有匹配的画师" /> : undefined
      }
      renderItem={({ item: a }) => (
        <Card
          onPress={() => {
            memory.set("artists:keyword", a.name || "");
            memory.set("scroll:artists", 0);
            memory.set("artists:filters", {
              status: "全部",
              order: "desc",
              size: 20,
              page: 1,
            });
            navigate({ name: "artists" });
          }}
          style={{ height: 72, padding: 10, marginBottom: 6 }}
        >
          <View style={[S.row, { gap: 12 }]}>
            <Media
              avatar
              path={a.icon}
              refreshKey={remote.revision}
              style={{ width: 46, height: 46, borderRadius: 13 }}
            />
            <View style={{ flex: 1 }}>
              <Txt bold lines={1}>
                {a.name}
              </Txt>
              <Txt size={12} muted lines={1}>
                {updateAge(a.last, now)}
              </Txt>
            </View>
            <Icon name="next" size={22} color={C.muted} />
          </View>
        </Card>
      )}
      ListFooterComponent={
        !!updates.length && count >= updates.length ? (
          <Txt size={12} muted style={{ textAlign: "center", padding: 16 }}>
            已显示全部画师
          </Txt>
        ) : undefined
      }
    />
  );
}

type ArtistFilters = {
  status: string;
  order: string;
  size: number;
  page: number;
};
export function Artists() {
  const { C, S } = useUI();
  const now = useClock();
  const { me, request, navigate, report } = useApp();
  const [keyword, setKeyword] = useSaved("artists:keyword", ""),
    [filters, setFilters] = useSaved<ArtistFilters>("artists:filters", {
      status: "全部",
      order: "desc",
      size: 20,
      page: 1,
    });
  const keywordQuery = useDebounced(keyword),
    [filterOpen, setFilterOpen] = useState(false),
    [draft, setDraft] = useState(filters),
    [menu, setMenu] = useState<Row | null>(null),
    [create, setCreate] = useState(false);
  const feedKey = JSON.stringify([
    keywordQuery,
    filters.status,
    filters.order,
    filters.size,
  ]);
  const [headerHeight, setHeaderHeight] = useState(130);
  const remote = usePagedRemote<Row>("artists:" + feedKey, (page) =>
    request(
      "GET",
      query("/api/fanbox", {
        page,
        pageSize: filters.size,
        keyword: keywordQuery,
        enabled:
          filters.status === "全部" ? undefined : filters.status === "已启用",
        lastUpdateOrder: filters.order,
      }),
    ),
  );
  const rows = remote.records;
  const mutate = async (url: string) => {
    try {
      await request("POST", url);
      await remote.reload();
    } catch (e) {
      report(e);
    }
  };
  const caps = menu?.capabilities || {},
    writable = !!menu && allowed(me, "update", menu.folderPath || "");
  return (
    <>
      <SavedList
        resetKey={feedKey}
        scrollKey="artists"
        ready={!remote.loading}
        expectedHeight={headerHeight + rows.length * 80 + 40}
        data={rows}
        keyExtractor={(a) => String(a.id)}
        contentContainerStyle={S.page}
        refreshControl={
          <RefreshControl
            refreshing={remote.refreshing}
            onRefresh={remote.refresh}
            colors={[C.blue]}
            progressBackgroundColor={C.surface}
          />
        }
        ListHeaderComponent={
          <View
            onLayout={(event) =>
              setHeaderHeight(event.nativeEvent.layout.height)
            }
          >
            {" "}
            <SearchBar
              value={keyword}
              onChange={(v) => {
                memory.set("scroll:artists", 0);
                setKeyword(v);
                setFilters({ ...filters, page: 1 });
              }}
              placeholder="搜索画师名称或 ID"
              onFilter={() => {
                setDraft(filters);
                setFilterOpen(true);
              }}
            />
            <Section
              title={`${remote.page ? remote.total : "—"} 位画师`}
              detail={`${filters.status} · ${filters.order === "desc" ? "最近更新" : "最早更新"}`}
              action={
                allowed(me, "update", "op:artist.create") ? (
                  <IconButton
                    name="plus"
                    label="新增画师"
                    filled
                    onPress={() => setCreate(true)}
                  />
                ) : undefined
              }
            />
          </View>
        }
        initialNumToRender={12}
        maxToRenderPerBatch={8}
        windowSize={7}
        removeClippedSubviews={false}
        getItemLayout={(_, index) => ({
          index,
          length: 80,
          offset: 16 + headerHeight + index * 80,
        })}
        onEndReached={() => void remote.next()}
        onEndReachedThreshold={0.6}
        ListEmptyComponent={
          <Empty
            loading={remote.loading}
            title={remote.error ? "加载暂时失败" : "没有匹配的画师"}
          />
        }
        ListFooterComponent={
          remote.loadingMore ? (
            <ActivityIndicator color={C.blue} style={{ padding: 20 }} />
          ) : remote.error ? (
            <Button
              quiet
              title="重新加载"
              onPress={() =>
                void (remote.page ? remote.next() : remote.refresh())
              }
            />
          ) : rows.length && !remote.hasMore ? (
            <Txt size={12} muted style={{ textAlign: "center", padding: 16 }}>
              已显示全部画师
            </Txt>
          ) : undefined
        }
        renderItem={({ item: a }) => (
          <ArtistCard
            artist={a}
            now={now}
            revision={remote.revision}
            menu={() => setMenu(a)}
            open={() => {
              if (
                a.capabilities?.canOpenGallery &&
                allowed(me, "read", a.folderPath || "")
              )
                navigate({
                  name: "gallery",
                  path: a.folderPath,
                  artistId: String(a.id),
                });
            }}
          />
        )}
      />
      <Sheet
        title="筛选画师"
        visible={filterOpen}
        onClose={() => setFilterOpen(false)}
      >
        <Options
          label="画师状态"
          values={["全部", "已启用", "已停用"].map((v) => ({
            label: v,
            value: v,
          }))}
          value={draft.status}
          onChange={(status) => setDraft({ ...draft, status })}
        />
        <Options
          label="更新时间"
          values={[
            { label: "最近更新优先", value: "desc" },
            { label: "最早更新优先", value: "asc" },
          ]}
          value={draft.order}
          onChange={(order) => setDraft({ ...draft, order })}
        />
        <Options
          label="每次加载"
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
            setFilters({ ...draft, page: 1 });
            memory.set("scroll:artists", 0);
            setFilterOpen(false);
          }}
        />
        <Button
          title="重置"
          quiet
          onPress={() => {
            setKeyword("");
            setFilters({ status: "全部", order: "desc", size: 20, page: 1 });
            setFilterOpen(false);
          }}
        />
      </Sheet>
      {menu && (
        <Menu
          title={menu.name}
          onClose={() => setMenu(null)}
          items={[
            ...(menu.url
              ? [
                  {
                    label: "画师主页",
                    icon: "external",
                    action: () => {
                      void Linking.openURL(menu.url).catch(report);
                    },
                  },
                ]
              : []),
            ...(caps.canStatus &&
            writable &&
            allowed(me, "update", "op:artist.status")
              ? [
                  {
                    label: menu.enable ? "停用抓取" : "启用抓取",
                    icon: "task",
                    action: () =>
                      void mutate(
                        `/api/fanbox/${menu.id}/status?enabled=${!menu.enable}`,
                      ),
                  },
                ]
              : []),
            ...(caps.canCompression &&
            writable &&
            allowed(me, "update", "op:artist.compression")
              ? [
                  {
                    label: menu.compression ? "关闭图片压缩" : "开启图片压缩",
                    icon: "gallery",
                    action: () =>
                      void mutate(
                        `/api/fanbox/${menu.id}/compression?enabled=${!menu.compression}`,
                      ),
                  },
                ]
              : []),
          ]}
        />
      )}{" "}
      {create && (
        <ArtistEditor
          close={() => setCreate(false)}
          saved={() => {
            setCreate(false);
            void remote.reload();
          }}
        />
      )}
    </>
  );
}
function ArtistCard({
  artist: a,
  now,
  revision,
  open,
  menu,
}: {
  artist: Row;
  now: number;
  revision: number;
  open(): void;
  menu(): void;
}) {
  const { C, S } = useUI(),
    banner = !!a.banner;
  const ink = banner ? "#f8fafc" : C.ink,
    muted = banner ? "#dbe7f3" : C.muted;
  const textShadow = banner
    ? {
        textShadowColor: "#00000088",
        textShadowRadius: 2,
        textShadowOffset: { width: 0, height: 1 },
      }
    : {};
  return (
    <Card
      onPress={open}
      style={{ height: 74, padding: 0, marginBottom: 6, borderRadius: 12 }}
    >
      {banner && (
        <>
          <Media
            path={a.banner}
            refreshKey={revision}
            style={StyleSheet.absoluteFill}
          />
          <View
            style={[StyleSheet.absoluteFill, { backgroundColor: "#07111d26" }]}
          />
        </>
      )}
      <View style={[S.row, { flex: 1, padding: 8, gap: 8 }]}>
        <View
          style={{
            borderRadius: 13,
            borderWidth: 1,
            borderColor: banner ? "#ffffffce" : C.line,
            backgroundColor: C.surface,
          }}
        >
          <Media
            avatar
            path={a.icon}
            refreshKey={revision}
            style={{ width: 42, height: 42, borderRadius: 11 }}
          />
        </View>
        <View
          style={{
            flex: 1,
            paddingHorizontal: 6,
            paddingVertical: 3,
          }}
        >
          <Txt
            size={16}
            bold
            lines={1}
            style={{
              color: ink,
              ...textShadow,
            }}
          >
            {a.name}
          </Txt>
          <View style={[S.row, { gap: 4 }]}>
            <View
              accessible
              accessibilityLabel={a.enable ? "已启用" : "已停用"}
            >
              <Icon
                name="power"
                size={14}
                shadow={banner}
                color={a.enable ? (banner ? "#8bdfff" : C.blue) : muted}
              />
            </View>
            {!!a.compression && (
              <View accessible accessibilityLabel="图片压缩已开启">
                <Icon name="compress" size={14} color={muted} shadow={banner} />
              </View>
            )}
            <Txt
              size={11}
              lines={1}
              style={{ color: muted, flexShrink: 1, ...textShadow }}
            >
              {updateAge(a.lastUpdate, now)}
            </Txt>
          </View>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="画师选项"
          onPress={menu}
          style={[
            S.iconButton,
            {
              backgroundColor: banner ? "transparent" : C.pale,
              borderRadius: 11,
              borderWidth: banner ? 0 : 1,
              borderColor: C.line,
            },
          ]}
        >
          <Icon
            name="more"
            color={banner ? "#ffffff" : C.blue}
            shadow={banner}
          />
        </Pressable>
      </View>
    </Card>
  );
}

function ArtistEditor({ close, saved }: { close(): void; saved(): void }) {
  const { C, S } = useUI();
  const { request, report } = useApp(),
    [url, setUrl] = useState(""),
    [name, setName] = useState(""),
    [compression, setCompression] = useState(true),
    [busy, setBusy] = useState(false);
  const save = async () => {
    if (!url.trim()) return;
    setBusy(true);
    try {
      await request("POST", "/api/fanbox", {
        url: url.trim(),
        name: name.trim(),
        compression,
      });
      saved();
    } catch (e) {
      report(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet title="新增画师" visible onClose={close}>
      <Input
        label="画师主页"
        placeholder="https://…"
        value={url}
        onChange={setUrl}
      />
      <Input label="名称（可选）" value={name} onChange={setName} />
      <View
        style={[S.row, { justifyContent: "space-between", marginBottom: 22 }]}
      >
        <Txt>图片压缩</Txt>
        <Switch
          value={compression}
          onValueChange={setCompression}
          trackColor={{ true: "#addcea" }}
          thumbColor={compression ? C.blue : "#c4ccd6"}
        />
      </View>
      <Button
        title="添加画师"
        busy={busy}
        disabled={!url.trim()}
        onPress={() => void save()}
      />
    </Sheet>
  );
}

export function FreeGalleries() {
  const { C, S } = useUI();
  const { request, me, navigate, report } = useApp(),
    [keyword, setKeyword] = useSaved("free:keyword", ""),
    debounced = useDebounced(keyword),
    [edit, setEdit] = useState<Row | null | undefined>(undefined),
    [menu, setMenu] = useState<Row | null>(null);
  const remote = useRemote<Row[]>(
    () => request("GET", query("/api/free-galleries", { keyword: debounced })),
    [debounced],
  );
  const rows = remote.data || [];
  const remove = (a: Row) =>
    confirm(
      "删除画廊登记？",
      () => {
        void request("DELETE", "/api/free-galleries/" + a.id)
          .then(() => remote.reload())
          .catch(report);
      },
      "文件夹和文件会保留。",
    );
  return (
    <>
      <Page
        scrollKey="free"
        loading={remote.loading}
        refreshing={remote.refreshing}
        onRefresh={remote.refresh}
      >
        <SearchBar
          value={keyword}
          onChange={setKeyword}
          placeholder="搜索画廊名称"
        />
        <Section
          title="我的画廊"
          detail={`${rows.length} 个画廊`}
          action={
            allowed(me, "update", "op:freeGallery.create") ? (
              <IconButton
                name="plus"
                label="新增画廊"
                filled
                onPress={() => setEdit(null)}
              />
            ) : undefined
          }
        />
        {remote.loading && !remote.data ? (
          <Empty loading />
        ) : !rows.length ? (
          <Empty title="暂无画廊" />
        ) : (
          rows.map((a) => (
            <Card
              key={a.id}
              onPress={() => {
                if (allowed(me, "read", a.folderPath))
                  navigate({ name: "gallery", path: a.folderPath });
              }}
              style={{ padding: 14 }}
            >
              <View style={[S.row, { gap: 12 }]}>
                <View
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 13,
                    backgroundColor: C.pale,
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Icon name="folder" size={36} />
                </View>
                <View style={{ flex: 1 }}>
                  <Txt size={17} bold>
                    {a.name}
                  </Txt>
                  <Txt muted size={12} lines={1}>
                    {a.folderPath}
                  </Txt>
                </View>
                {(allowed(me, "update", a.folderPath) &&
                  allowed(me, "update", "op:freeGallery.update")) ||
                (allowed(me, "delete", a.folderPath) &&
                  allowed(me, "delete", "op:freeGallery.delete")) ? (
                  <IconButton
                    name="more"
                    label="画廊选项"
                    onPress={() => setMenu(a)}
                  />
                ) : (
                  <Icon name="next" size={22} color={C.muted} />
                )}
              </View>
            </Card>
          ))
        )}
      </Page>
      {menu && (
        <Menu
          title={menu.name}
          onClose={() => setMenu(null)}
          items={[
            ...(allowed(me, "read", menu.folderPath)
              ? [
                  {
                    label: "打开图库",
                    icon: "gallery",
                    action: () =>
                      navigate({ name: "gallery", path: menu.folderPath }),
                  },
                ]
              : []),
            ...(allowed(me, "update", menu.folderPath) &&
            allowed(me, "update", "op:freeGallery.update")
              ? [
                  {
                    label: "编辑画廊",
                    icon: "edit",
                    action: () => setEdit(menu),
                  },
                ]
              : []),
            ...(allowed(me, "delete", menu.folderPath) &&
            allowed(me, "delete", "op:freeGallery.delete")
              ? [
                  {
                    label: "删除画廊",
                    icon: "trash",
                    danger: true,
                    action: () => remove(menu),
                  },
                ]
              : []),
          ]}
        />
      )}{" "}
      {edit !== undefined && (
        <GalleryEditor
          value={edit}
          close={() => setEdit(undefined)}
          saved={() => {
            setEdit(undefined);
            void remote.reload();
          }}
        />
      )}
    </>
  );
}
function GalleryEditor({
  value,
  close,
  saved,
}: {
  value: Row | null;
  close(): void;
  saved(): void;
}) {
  const { request, report } = useApp(),
    [name, setName] = useState(value?.name || ""),
    [path, setPath] = useState(value?.folderPath || ""),
    [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await request(
        value ? "PUT" : "POST",
        "/api/free-galleries" + (value ? "/" + value.id : ""),
        { name: name.trim(), folderPath: path.trim() },
      );
      saved();
    } catch (e) {
      report(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet visible title={value ? "编辑画廊" : "新增画廊"} onClose={close}>
      <Input label="画廊名称" value={name} onChange={setName} />
      <Input
        label="文件夹绝对路径"
        placeholder="服务器上的文件夹路径"
        value={path}
        onChange={setPath}
      />
      <Button
        title="保存"
        busy={busy}
        disabled={!name.trim() || !path.trim()}
        onPress={() => void save()}
      />
    </Sheet>
  );
}
