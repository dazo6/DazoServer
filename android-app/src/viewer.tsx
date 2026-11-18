import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Animated,
  FlatList,
  Modal,
  PanResponder,
  Pressable,
  StatusBar,
  View,
  requireNativeComponent,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { fileUrl, Native, Row, upcomingImageUrls } from "./core";
import { useApp, useSaved } from "./context";
import { useMediaPrefetch } from "./media";
import { Icon, IconButton, Media, Txt } from "./ui";
import { useUI } from "./theme";
import { useDownloads } from "./downloads";
import { PlaybackSpeedSheet } from "./playback";

const Video = requireNativeComponent<{
  path: string;
  rate: number;
  style: any;
}>("DazoVideo");
export function Viewer({
  items,
  path,
  index,
  setIndex,
  close,
}: {
  items: Row[];
  path: string;
  index: number;
  setIndex(index: number): void;
  close(): void;
}) {
  const { S } = useUI(),
    { scope } = useApp(),
    { jobs, download } = useDownloads();
  const inset = useSafeAreaInsets(),
    { width, height } = useWindowDimensions();
  const pages = useMemo(
    () =>
      items.flatMap((item, original) =>
        item.type === "folder" ? [] : [{ item, original }],
      ),
    [items],
  );
  const pageIndex = Math.max(
    0,
    pages.findIndex((page) => page.original === index),
  );
  const pager = useRef<FlatList<(typeof pages)[number]>>(null),
    shown = useRef(pageIndex);
  const [paging, setPaging] = useState(true),
    [rate, setRate] = useSaved("settings:videoRate", 1),
    [rateOpen, setRateOpen] = useState(false);
  const item = items[index],
    source = fileUrl(path, item.name),
    job = jobs.find((value) => value.path === source),
    working = job?.phase === "queued" || job?.phase === "downloading";
  const pageHeight = Math.max(100, height - inset.top - inset.bottom - 92);
  useMediaPrefetch(scope, upcomingImageUrls(path, items, index));
  useEffect(() => {
    setPaging(true);
    if (shown.current !== pageIndex) {
      shown.current = pageIndex;
      pager.current?.scrollToOffset({
        offset: pageIndex * width,
        animated: true,
      });
    }
  }, [pageIndex, width]);
  const next = (direction: number) => {
    const target = pages[pageIndex + direction];
    if (target) setIndex(target.original);
  };
  const save = async () => {
    if (working) return;
    if (job?.phase === "complete") {
      Native.openDownload(job.id).catch((error) =>
        Alert.alert("无法打开", error.message),
      );
      return;
    }
    try {
      await download(source, item.name);
    } catch (_) {}
  };
  return (
    <Modal
      visible
      animationType="fade"
      onRequestClose={close}
      statusBarTranslucent
      navigationBarTranslucent
    >
      <StatusBar barStyle="light-content" />
      <View
        style={{
          flex: 1,
          backgroundColor: "#080d15",
          paddingTop: inset.top,
          paddingBottom: inset.bottom,
        }}
      >
        <View style={[S.row, { height: 46, paddingHorizontal: 2 }]}>
          <IconButton name="back" label="返回图库" onPress={close} />
          <View style={{ flex: 1, paddingHorizontal: 4 }}>
            <Txt size={14} style={{ color: "#e2e8f0" }}>
              {pageIndex + 1} / {pages.length}
            </Txt>
          </View>
          {!!working && (
            <Txt size={12} style={{ color: "#8ed9ff" }}>
              {job && job.total > 0
                ? `保存中 ${Math.min(100, Math.round((job.bytes * 100) / job.total))}%`
                : "准备下载…"}
            </Txt>
          )}
          <IconButton
            name={job?.phase === "complete" ? "folder" : "download"}
            label={job?.phase === "complete" ? "打开已下载文件" : "下载原文件"}
            disabled={working}
            onPress={() => void save()}
          />
          {item.type === "video" && (
            <Pressable
              accessibilityLabel="播放速度"
              onPress={() => setRateOpen(true)}
              style={{ padding: 12 }}
            >
              <Txt size={13} style={{ color: "white" }}>
                {rate}×
              </Txt>
            </Pressable>
          )}
        </View>
        <View style={{ flex: 1, overflow: "hidden" }}>
          <FlatList
            key={width}
            ref={pager}
            horizontal
            pagingEnabled
            scrollEnabled={paging}
            data={pages}
            keyExtractor={(page) => page.item.name}
            initialScrollIndex={pageIndex}
            initialNumToRender={3}
            maxToRenderPerBatch={3}
            windowSize={3}
            removeClippedSubviews={false}
            showsHorizontalScrollIndicator={false}
            getItemLayout={(_, i) => ({
              index: i,
              length: width,
              offset: i * width,
            })}
            onMomentumScrollEnd={(event) => {
              const page = Math.max(
                0,
                Math.min(
                  pages.length - 1,
                  Math.round(event.nativeEvent.contentOffset.x / width),
                ),
              );
              shown.current = page;
              setIndex(pages[page].original);
            }}
            renderItem={({ item: page }) => (
              <View
                style={{
                  width,
                  height: pageHeight,
                  backgroundColor: "#080d15",
                }}
              >
                {page.item.type === "video" ? (
                  page.original === index ? (
                    <Video
                      path={fileUrl(path, page.item.name)}
                      rate={rate}
                      style={{ flex: 1, backgroundColor: "black" }}
                    />
                  ) : (
                    <View style={{ flex: 1, backgroundColor: "black" }}>
                      <Media
                        path={fileUrl(path, page.item.name, true)}
                        contain
                        style={{ flex: 1, backgroundColor: "black" }}
                      />
                      <View
                        style={{
                          position: "absolute",
                          alignSelf: "center",
                          top: "45%",
                        }}
                      >
                        <Icon name="play" color="white" size={48} />
                      </View>
                    </View>
                  )
                ) : (
                  <ZoomImage
                    path={fileUrl(path, page.item.name)}
                    width={width}
                    height={pageHeight}
                    active={page.original === index}
                    setPaging={setPaging}
                  />
                )}
              </View>
            )}
          />
        </View>
        <View
          style={[
            S.row,
            { justifyContent: "center", gap: 32, paddingVertical: 2 },
          ]}
        >
          <IconButton
            name="previous"
            label="上一张"
            disabled={pageIndex <= 0}
            onPress={() => next(-1)}
          />
          <IconButton
            name="next"
            label="下一张"
            disabled={pageIndex >= pages.length - 1}
            onPress={() => next(1)}
          />
        </View>
        <PlaybackSpeedSheet
          visible={rateOpen}
          rate={rate}
          setRate={setRate}
          close={() => setRateOpen(false)}
        />
      </View>
    </Modal>
  );
}
function ZoomImage({
  path,
  width,
  height,
  active,
  setPaging,
}: {
  path: string;
  width: number;
  height: number;
  active: boolean;
  setPaging(value: boolean): void;
}) {
  const scale = useRef(new Animated.Value(1)).current,
    tx = useRef(new Animated.Value(0)).current,
    ty = useRef(new Animated.Value(0)).current;
  const g = useRef({
    zoom: 1,
    x: 0,
    y: 0,
    distance: 0,
    startZoom: 1,
    multi: false,
    lastTap: 0,
    startX: 0,
    startY: 0,
  });
  const bound = (value: number, size: number) =>
    Math.max(
      (-size * (g.current.zoom - 1)) / 2,
      Math.min((size * (g.current.zoom - 1)) / 2, value),
    );
  const reset = () => {
    g.current.zoom = 1;
    g.current.x = 0;
    g.current.y = 0;
    scale.setValue(1);
    tx.setValue(0);
    ty.setValue(0);
  };
  useEffect(() => {
    if (!active) reset();
  }, [active]);
  const end = (_: any, state: any) => {
    const value = g.current;
    if (value.zoom <= 1.05) {
      reset();
      setPaging(true);
    } else {
      value.x = bound(value.x + state.dx, width);
      value.y = bound(value.y + state.dy, height);
      setPaging(false);
    }
  };
  const responder = PanResponder.create({
    onStartShouldSetPanResponder: (event) =>
      g.current.zoom > 1 || event.nativeEvent.touches.length > 1,
    onMoveShouldSetPanResponder: (event) =>
      g.current.zoom > 1 || event.nativeEvent.touches.length > 1,
    onMoveShouldSetPanResponderCapture: (event) =>
      event.nativeEvent.touches.length > 1,
    onPanResponderTerminationRequest: () => false,
    onPanResponderGrant: () => {
      g.current.distance = 0;
      g.current.startZoom = g.current.zoom;
      g.current.multi = false;
      setPaging(false);
    },
    onPanResponderMove: (event, state) => {
      const value = g.current,
        touches = event.nativeEvent.touches;
      if (touches.length > 1) {
        value.multi = true;
        const distance = Math.hypot(
          touches[0].pageX - touches[1].pageX,
          touches[0].pageY - touches[1].pageY,
        );
        if (!value.distance) value.distance = distance;
        value.zoom = Math.max(
          1,
          Math.min(5, (value.startZoom * distance) / value.distance),
        );
        scale.setValue(value.zoom);
      } else if (value.zoom > 1 && !value.multi) {
        tx.setValue(bound(value.x + state.dx, width));
        ty.setValue(bound(value.y + state.dy, height));
      }
    },
    onPanResponderRelease: end,
    onPanResponderTerminate: end,
  });
  return (
    <View
      style={{ flex: 1, overflow: "hidden" }}
      {...responder.panHandlers}
      onTouchStart={(event) => {
        g.current.startX = event.nativeEvent.pageX;
        g.current.startY = event.nativeEvent.pageY;
        g.current.multi = event.nativeEvent.touches.length > 1;
      }}
      onTouchEnd={(event) => {
        const value = g.current;
        if (
          value.multi ||
          Math.abs(event.nativeEvent.pageX - value.startX) > 8 ||
          Math.abs(event.nativeEvent.pageY - value.startY) > 8
        )
          return;
        const now = Date.now();
        if (now - value.lastTap < 280) {
          value.zoom = value.zoom > 1 ? 1 : 2;
          value.x = 0;
          value.y = 0;
          setPaging(value.zoom === 1);
          Animated.parallel([
            Animated.spring(scale, {
              toValue: value.zoom,
              useNativeDriver: true,
            }),
            Animated.spring(tx, { toValue: 0, useNativeDriver: true }),
            Animated.spring(ty, { toValue: 0, useNativeDriver: true }),
          ]).start();
        }
        value.lastTap = now;
      }}
    >
      <Animated.View
        style={{
          flex: 1,
          transform: [{ translateX: tx }, { translateY: ty }, { scale }],
        }}
      >
        <Media
          path={path}
          contain
          style={{ flex: 1, backgroundColor: "#080d15" }}
        />
      </Animated.View>
    </View>
  );
}
