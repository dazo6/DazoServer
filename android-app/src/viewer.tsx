import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Animated,
  Easing,
  FlatList,
  Modal,
  PanResponder,
  Pressable,
  StatusBar,
  View,
  StyleSheet,
  ToastAndroid,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import {
  fileUrl,
  galleryMediaUrl,
  memory,
  Native,
  Row,
  upcomingImageUrls,
} from "./core";
import { useApp, useSaved } from "./context";
import { loadedMedia, useMediaPrefetch } from "./media";
import { Icon, IconButton, Media, Txt } from "./ui";
import { useUI } from "./theme";
import { useDownloads } from "./downloads";
import { PlaybackSpeedSheet } from "./playback";
import { fitImageRect, ImageTransition } from "./gallery-playback";
import { NativeVideo as Video } from "./video";

export function Viewer({
  items,
  path,
  index,
  setIndex,
  close,
  origin,
  originalImages,
  closeTarget,
}: {
  items: Row[];
  path: string;
  index: number;
  setIndex(index: number): void;
  close(): void;
  origin: ImageTransition | null;
  originalImages: boolean;
  closeTarget(index: number): Promise<ImageTransition | null>;
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
  const progress = useRef(new Animated.Value(0)).current;
  const [transition, setTransition] = useState(origin),
    [overlay, setOverlay] = useState(!!origin),
    [transitioning, setTransitioning] = useState(true);
  const closing = useRef(false);
  const entrance = useRef({ shown: false, ready: !origin, started: false });
  const enterTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  const item = items[index],
    source = fileUrl(path, item.name),
    job = jobs.find((value) => value.path === source),
    working = job?.phase === "queued" || job?.phase === "downloading";
  const pageHeight = Math.max(100, height - inset.top - inset.bottom - 92);
  const snapshot =
    loadedMedia(scope, source) ||
    loadedMedia(scope, galleryMediaUrl(path, item, originalImages));
  const ratio = snapshot
    ? snapshot.width / snapshot.height
    : memory.get(
        "ratio:" + source,
        transition ? transition.rect.width / transition.rect.height : 1,
      );
  const target = fitImageRect(ratio, width, pageHeight, inset.top + 46);
  useEffect(
    () => () => {
      progress.stopAnimation();
      if (enterTimer.current) clearTimeout(enterTimer.current);
    },
    [progress],
  );
  const enter = () =>
    Animated.timing(progress, {
      toValue: 1,
      duration: 240,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setOverlay(false);
        setTransitioning(false);
      }
    });
  const startEntrance = () => {
    const state = entrance.current;
    if (!state.shown || !state.ready || state.started || closing.current)
      return;
    state.started = true;
    if (enterTimer.current) clearTimeout(enterTimer.current);
    requestAnimationFrame(enter);
  };
  const requestClose = async () => {
    if (closing.current) return;
    closing.current = true;
    progress.stopAnimation();
    const destination = await closeTarget(index);
    setTransition(destination);
    setOverlay(!!destination);
    setTransitioning(true);
    progress.setValue(1);
    requestAnimationFrame(() =>
      Animated.timing(progress, {
        toValue: 0,
        duration: destination ? 210 : 160,
        easing: Easing.inOut(Easing.cubic),
        useNativeDriver: true,
      }).start(({ finished }) => {
        if (finished) close();
      }),
    );
  };
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
        ToastAndroid.show(error.message || "暂时无法打开", ToastAndroid.SHORT),
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
      transparent
      animationType="none"
      onShow={() => {
        entrance.current.shown = true;
        startEntrance();
        if (!entrance.current.started)
          enterTimer.current = setTimeout(() => {
            entrance.current.ready = true;
            startEntrance();
          }, 600);
      }}
      onRequestClose={() => void requestClose()}
      statusBarTranslucent
      navigationBarTranslucent
    >
      <StatusBar barStyle="light-content" />
      <View style={{ flex: 1 }}>
        <Animated.View
          pointerEvents="none"
          style={[
            StyleSheet.absoluteFill,
            { backgroundColor: "#080d15", opacity: progress },
          ]}
        />
        <Animated.View
          pointerEvents={transitioning ? "none" : "auto"}
          style={{
            flex: 1,
            opacity: overlay ? 0 : progress,
            paddingTop: inset.top,
            paddingBottom: inset.bottom,
          }}
        >
          <View style={[S.row, { height: 46, paddingHorizontal: 2 }]}>
            <IconButton
              name="back"
              label="返回图库"
              onPress={() => void requestClose()}
            />
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
              label={
                job?.phase === "complete" ? "打开已下载文件" : "下载原文件"
              }
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
                      fallbackPath={galleryMediaUrl(
                        path,
                        page.item,
                        originalImages,
                      )}
                      width={width}
                      height={pageHeight}
                      active={page.original === index && !transitioning}
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
        </Animated.View>
        {overlay && transition && (
          <Animated.View
            pointerEvents="none"
            style={{
              position: "absolute",
              left: target.x,
              top: target.y,
              width: target.width,
              height: target.height,
              transform: [
                {
                  translateX: progress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [
                      transition.rect.x +
                        transition.rect.width / 2 -
                        target.x -
                        target.width / 2,
                      0,
                    ],
                  }),
                },
                {
                  translateY: progress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [
                      transition.rect.y +
                        transition.rect.height / 2 -
                        target.y -
                        target.height / 2,
                      0,
                    ],
                  }),
                },
                {
                  scaleX: progress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [transition.rect.width / target.width, 1],
                  }),
                },
                {
                  scaleY: progress.interpolate({
                    inputRange: [0, 1],
                    outputRange: [transition.rect.height / target.height, 1],
                  }),
                },
              ],
            }}
          >
            <Media
              path={transition.path}
              onReady={() => {
                entrance.current.ready = true;
                startEntrance();
              }}
              contain
              animate={false}
              style={{ flex: 1, backgroundColor: "transparent" }}
            />
          </Animated.View>
        )}
      </View>
    </Modal>
  );
}
function ZoomImage({
  path,
  fallbackPath,
  width,
  height,
  active,
  setPaging,
}: {
  path: string;
  fallbackPath: string;
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
          fallbackPath={fallbackPath}
          animate={active}
          contain
          style={{ flex: 1, backgroundColor: "#080d15" }}
        />
      </Animated.View>
    </View>
  );
}
