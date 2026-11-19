import { Viewer } from "../viewer";
import { useDownloads } from "../downloads";
import { useUI } from "../theme";
import React, { useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Animated,
  DeviceEventEmitter,
  FlatList,
  Modal,
  PanResponder,
  Pressable,
  RefreshControl,
  StatusBar,
  View,
  requireNativeComponent,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import {
  allowed,
  dateLabel,
  fileUrl,
  memory,
  Native,
  query,
  Row,
  galleryRestoreOffset,
  galleryMediaUrl,
  upcomingImageUrls,
} from "../core";
import {
  NavigationTab,
  ScreenActive,
  useApp,
  useRemote,
  useSaved,
  useScreenActive,
} from "../context";
import { useMediaPrefetch } from "../media";
import {
  Button,
  Card,
  Empty,
  Icon,
  IconButton,
  Media,
  Menu,
  Options,
  Sheet,
  Txt,
} from "../ui";

type Position = { offset: number; anchor: string; within: number };
export function Gallery({
  path,
  artistId,
  header,
}: {
  path: string;
  artistId?: string;
  header: React.ReactNode;
}) {
  const { C, S } = useUI();
  const { download: saveFile } = useDownloads();
  const active = useScreenActive();
  const tab = useContext(NavigationTab);
  const activeRef = useRef(active);
  activeRef.current = active;
  const { me, scope, originalImages, request, navigate, report, toast } =
      useApp(),
    width = useWindowDimensions().width - 4;
  const [sort, setSort] = useSaved("gallery:sort:" + path, {
      field: "album",
      order: "desc",
    }),
    [sortOpen, setSortOpen] = useState(false),
    [draft, setDraft] = useState(sort),
    [menu, setMenu] = useState(false),
    [fileMenu, setFileMenu] = useState<Row | null>(null),
    [viewer, setViewer] = useState<number | null>(null),
    [upload, setUpload] = useState<number | null>(null);
  const remote = useRemote<Row>(
    () =>
      request(
        "GET",
        query("/api/fanbox/gallery", {
          folderPath: path,
          sortBy: sort.field,
          order: sort.order,
        }),
      ),
    [path, sort.field, sort.order],
  );
  const items: Row[] = remote.data?.images || [],
    ref = useRef<FlatList<Row>>(null),
    positionKey = "gallery:position:" + tab + ":" + path,
    readerKey = "gallery:reader:" + tab + ":" + path;
  const saved = useRef<Position>(
      memory.get(
        positionKey,
        memory.get("gallery:position:" + path, {
          offset: 0,
          anchor: "",
          within: 0,
        }),
      ),
    ),
    live = useRef({ ...saved.current }),
    restored = useRef(false),
    restoreTarget = useRef<number | null>(null),
    first = useRef(0),
    [viewportHeight, setViewportHeight] = useState(0),
    [contentHeight, setContentHeight] = useState(0),
    [prefetchFrom, setPrefetchFrom] = useState(0),
    [visibleImages, setVisibleImages] = useState<Set<string>>(() => new Set()),
    [ratios, setRatios] = useState<Record<string, number>>({});
  const reader = memory.get<string>(
    readerKey,
    memory.get("gallery:reader:" + path, ""),
  );
  const mediaPath = (item: Row) => galleryMediaUrl(path, item, originalImages);
  useMediaPrefetch(
    scope,
    active && originalImages && viewer === null
      ? upcomingImageUrls(path, items, prefetchFrom)
      : [],
  );
  const layouts = useMemo(() => {
    let offset = 94;
    return items.map((item) => {
      const url = mediaPath(item),
        ratio = ratios[item.name] || memory.get("ratio:" + url, 1);
      const length =
        item.type === "folder" && !item.posterUrl
          ? 64
          : width / ratio + (item.type === "folder" ? 44 : 0) + 2;
      const result = { length, offset };
      offset += length;
      return result;
    });
  }, [items, width, ratios, path, originalImages]);
  const layoutRef = useRef(layouts);
  layoutRef.current = layouts;
  useEffect(() => {
    if (
      !active ||
      !items.length ||
      restored.current ||
      !viewportHeight ||
      !contentHeight
    )
      return;
    const offset = galleryRestoreOffset(
      items,
      layouts,
      saved.current,
      viewportHeight,
      contentHeight,
    );
    if (offset === null) return;
    restoreTarget.current = offset;
    let timer: ReturnType<typeof setTimeout> | undefined,
      attempts = 0;
    const apply = () => {
      if (restored.current || !activeRef.current) return;
      ref.current?.scrollToOffset({ offset, animated: false });
      if (offset < 1) {
        live.current.offset = 0;
        restored.current = true;
      } else if (++attempts < 12) timer = setTimeout(apply, 120);
    };
    const frame = requestAnimationFrame(apply);
    return () => {
      cancelAnimationFrame(frame);
      if (timer) clearTimeout(timer);
    };
  }, [active, layouts, viewportHeight, contentHeight]);
  useEffect(() => {
    const subscription = DeviceEventEmitter.addListener(
      "DazoUploadProgress",
      (event) => setUpload(event.percent),
    );
    return () => subscription.remove();
  }, []);
  const uploadFile = async () => {
    setUpload(0);
    try {
      await Native.pickUpload(path);
      toast("上传完成");
      await remote.reload();
    } catch (e) {
      report(e);
    } finally {
      setUpload(null);
    }
  };
  const download = async (item?: Row) => {
    try {
      if (item) {
        await saveFile(fileUrl(path, item.name), item.name);
      } else {
        const result = await request<Row>(
          "GET",
          query("/api/fanbox/gallery/archive", { folderPath: path }),
        );
        await saveFile(result.url, result.filename || "gallery.zip");
      }
      toast("已加入下载队列");
    } catch (e) {
      report(e);
    }
  };
  const locate = (index: number) => {
    const offset = layoutRef.current[index]?.offset || 0;
    first.current = index;
    restored.current = true;
    live.current = { offset, anchor: items[index].name, within: 0 };
    saved.current = { ...live.current };
    memory.set(positionKey, { ...live.current });
    ref.current?.scrollToOffset({ offset, animated: false });
  };
  const open = (item: Row, index: number) => {
    memory.set(positionKey, { ...live.current });
    if (item.type === "folder") {
      navigate({ name: "gallery", path: item.folderPath, artistId });
    } else {
      memory.set(readerKey, item.name);
      locate(index);
      setViewer(index);
    }
  };
  const ratioChanged = (index: number, item: Row, ratio: number) => {
    const previous = layoutRef.current[index]?.length || width + 2,
      newLength = width / ratio + (item.type === "folder" ? 44 : 0) + 2;
    setRatios((old) =>
      old[item.name] === ratio ? old : { ...old, [item.name]: ratio },
    );
    if (
      restored.current &&
      index < first.current &&
      Math.abs(newLength - previous) > 1
    ) {
      live.current.offset += newLength - previous;
      requestAnimationFrame(() =>
        ref.current?.scrollToOffset({
          offset: live.current.offset,
          animated: false,
        }),
      );
    }
  };
  const onViewable = useRef(({ viewableItems }: any) => {
    setVisibleImages(
      new Set(viewableItems.map((entry: any) => entry.item.name)),
    );
    if (!activeRef.current || !restored.current) return;
    const visible = viewableItems[0];
    if (visible) {
      first.current = visible.index || 0;
      setPrefetchFrom(first.current);
      live.current.anchor = visible.item.name;
      live.current.within =
        live.current.offset - (layoutRef.current[first.current]?.offset || 0);
      memory.set(positionKey, { ...live.current });
    }
  }).current;
  return (
    <View style={{ flex: 1 }}>
      <ScreenActive.Provider value={active && viewer === null}>
        <FlatList
          ref={ref}
          ListHeaderComponent={
            <>
              {header}
              <View
                style={[
                  S.row,
                  {
                    backgroundColor: C.surface,
                    paddingLeft: 8,
                    paddingRight: 2,
                    borderBottomWidth: 1,
                    borderColor: C.line,
                    minHeight: 44,
                  },
                ]}
              >
                <Txt size={12} muted style={{ flex: 1 }}>
                  {items.length} 项
                </Txt>
                {!!reader && items.some((a) => a.name === reader) && (
                  <Pressable
                    onPress={() => {
                      const current = items.findIndex((a) => a.name === reader);
                      locate(current);
                      setViewer(current);
                    }}
                    style={{ paddingVertical: 6, paddingHorizontal: 8 }}
                  >
                    <Txt size={12} bold style={{ color: C.blue }}>
                      继续浏览
                    </Txt>
                  </Pressable>
                )}
                <IconButton
                  name="sort"
                  label="图库排序"
                  onPress={() => {
                    setDraft(sort);
                    setSortOpen(true);
                  }}
                />
                {allowed(me, "update", path) && (
                  <IconButton
                    name="upload"
                    label="上传文件"
                    onPress={() => void uploadFile()}
                  />
                )}
                <IconButton
                  name="more"
                  label="图库选项"
                  onPress={() => setMenu(true)}
                />
              </View>
            </>
          }
          data={items}
          keyExtractor={(item) => item.name}
          contentContainerStyle={{
            paddingHorizontal: 2,
            paddingTop: 2,
            paddingBottom: 2,
            flexGrow: 1,
          }}
          refreshControl={
            <RefreshControl
              refreshing={remote.refreshing}
              onRefresh={remote.refresh}
              colors={[C.blue]}
            />
          }
          initialNumToRender={4}
          maxToRenderPerBatch={4}
          windowSize={5}
          removeClippedSubviews={false}
          getItemLayout={(_, index) => ({ ...layouts[index], index })}
          onLayout={(event) =>
            setViewportHeight(event.nativeEvent.layout.height)
          }
          onContentSizeChange={(_, height) => setContentHeight(height)}
          onViewableItemsChanged={onViewable}
          viewabilityConfig={{ itemVisiblePercentThreshold: 1 }}
          onScrollBeginDrag={() => {
            restored.current = true;
          }}
          onScroll={(event) => {
            if (!active) return;
            const offset = event.nativeEvent.contentOffset.y;
            // The initial layout emits y=0 before the saved scroll command is applied.
            if (!restored.current) {
              if (
                restoreTarget.current === null ||
                Math.abs(offset - restoreTarget.current) > 2
              )
                return;
              restored.current = true;
            }
            const index = layoutRef.current.findIndex(
              (layout) => layout.offset + layout.length > offset,
            );
            first.current = Math.max(0, index);
            live.current = {
              offset,
              anchor: items[first.current]?.name || "",
              within: offset - (layoutRef.current[first.current]?.offset || 0),
            };
            memory.set(positionKey, { ...live.current });
          }}
          scrollEventThrottle={32}
          ListEmptyComponent={
            <Empty loading={remote.loading} title="这个文件夹还是空的" />
          }
          renderItem={({ item, index }) =>
            item.type === "folder" && !item.posterUrl ? (
              <Card
                onPress={() => open(item, index)}
                style={{
                  height: 62,
                  borderRadius: 0,
                  marginBottom: 2,
                  padding: 8,
                }}
              >
                <View style={[S.row, { gap: 12 }]}>
                  <Icon name="folder" size={34} />
                  <Txt bold style={{ flex: 1 }} lines={1}>
                    {item.name}
                  </Txt>
                  <Icon name="next" size={22} />
                </View>
              </Card>
            ) : (
              <Pressable
                accessibilityLabel={item.name}
                onPress={() => open(item, index)}
                onLongPress={() => setFileMenu(item)}
                delayLongPress={450}
                style={{
                  backgroundColor: C.surface,
                  marginBottom: 2,
                }}
              >
                <View>
                  <Media
                    path={mediaPath(item)}
                    animate={viewer === null && visibleImages.has(item.name)}
                    aspect
                    refreshKey={remote.revision}
                    onRatio={(ratio) => ratioChanged(index, item, ratio)}
                  />
                  {item.type === "video" && (
                    <View
                      style={{
                        position: "absolute",
                        top: "45%",
                        alignSelf: "center",
                        borderRadius: 24,
                        backgroundColor: "#17203399",
                        width: 48,
                        height: 48,
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <Icon name="play" color="white" size={32} />
                    </View>
                  )}
                </View>
                {item.type === "folder" && (
                  <View
                    style={[
                      S.row,
                      { height: 44, paddingHorizontal: 12, gap: 8 },
                    ]}
                  >
                    {item.type === "folder" && <Icon name="folder" size={22} />}
                    <Txt size={13} lines={1} style={{ flex: 1 }}>
                      {item.name}
                    </Txt>
                    <Pressable
                      accessibilityLabel="文件选项"
                      hitSlop={8}
                      onPress={() => setFileMenu(item)}
                    >
                      <Icon name="more" size={24} color={C.muted} />
                    </Pressable>
                  </View>
                )}
              </Pressable>
            )
          }
        />
      </ScreenActive.Provider>
      <Sheet
        title="图库排序"
        visible={sortOpen}
        onClose={() => setSortOpen(false)}
      >
        <Options
          label="排序方式"
          values={[
            { label: "相册顺序", value: "album" },
            { label: "创建时间", value: "created" },
            { label: "文件名", value: "name" },
            { label: "随机", value: "random" },
          ]}
          value={draft.field}
          onChange={(field) => setDraft({ ...draft, field })}
        />
        <Options
          label="排列方向"
          values={[
            { label: "降序", value: "desc" },
            { label: "升序", value: "asc" },
          ]}
          value={draft.order}
          onChange={(order) => setDraft({ ...draft, order })}
        />
        <Button
          title="应用"
          onPress={() => {
            setSort(draft);
            setSortOpen(false);
            saved.current = { offset: 0, anchor: "", within: 0 };
            memory.set(positionKey, saved.current);
            live.current = { ...saved.current };
            restoreTarget.current = 0;
            restored.current = true;
            ref.current?.scrollToOffset({ offset: 0, animated: false });
          }}
        />
      </Sheet>
      {menu && (
        <Menu
          title="图库"
          onClose={() => setMenu(false)}
          items={[
            {
              label: "打包下载",
              icon: "download",
              action: () => void download(),
            },
            {
              label: "查看路径",
              icon: "folder",
              action: () => Alert.alert("当前位置", path),
            },
          ]}
        />
      )}{" "}
      {fileMenu && (
        <Menu
          title={fileMenu.name}
          onClose={() => setFileMenu(null)}
          items={[
            {
              label: fileMenu.type === "folder" ? "打开文件夹" : "打开文件",
              icon: fileMenu.type === "folder" ? "folder" : "gallery",
              action: () =>
                open(
                  fileMenu,
                  items.findIndex((a) => a.name === fileMenu.name),
                ),
            },
            ...(fileMenu.type !== "folder"
              ? [
                  {
                    label: "下载原文件",
                    icon: "download",
                    action: () => void download(fileMenu),
                  },
                ]
              : []),
            {
              label: "文件信息",
              icon: "more",
              action: () =>
                Alert.alert(
                  fileMenu.name,
                  `类型　${fileMenu.type}\n创建时间　${dateLabel(fileMenu.createdAt)}`,
                ),
            },
          ]}
        />
      )}
      <Sheet
        visible={upload !== null}
        title="上传文件"
        onClose={() => {
          Native.cancelUpload();
          setUpload(null);
        }}
      >
        <Txt size={13} muted style={{ marginBottom: 16 }}>
          {upload === 0 ? "正在准备文件…" : `已上传 ${upload}%`}
        </Txt>
        <View
          style={{
            height: 6,
            backgroundColor: C.bg,
            borderRadius: 3,
            marginBottom: 24,
          }}
        >
          <View
            style={{
              height: 6,
              width: `${upload || 0}%`,
              backgroundColor: C.blue,
              borderRadius: 3,
            }}
          />
        </View>
        <Button
          quiet
          title="取消上传"
          onPress={() => {
            Native.cancelUpload();
            setUpload(null);
          }}
        />
      </Sheet>
      {viewer !== null && items[viewer] && (
        <Viewer
          items={items}
          path={path}
          index={viewer}
          setIndex={(index) => {
            locate(index);
            setViewer(index);
            memory.set(readerKey, items[index].name);
          }}
          close={() => {
            const current = viewer;
            setViewer(null);
            requestAnimationFrame(() => locate(current));
          }}
        />
      )}
    </View>
  );
}
