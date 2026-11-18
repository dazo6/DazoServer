import React, { useEffect, useRef, useState } from "react";
import { FlatList, FlatListProps } from "react-native";
import { memory } from "./core";
import { useScreenActive } from "./context";

/** Persist only position and page counts; server records stay in memory. */
export function SavedList<T>({
  scrollKey,
  ready,
  expectedHeight,
  resetKey,
  ...props
}: FlatListProps<T> & {
  scrollKey: string;
  ready: boolean;
  expectedHeight: number;
  resetKey?: string;
}) {
  const ref = useRef<FlatList<T>>(null),
    saved = useRef(memory.get("scroll:" + scrollKey, 0)),
    restored = useRef(false),
    target = useRef<number | null>(null);
  const active = useScreenActive(),
    [viewport, setViewport] = useState(0),
    [content, setContent] = useState(0);
  const previous = useRef(resetKey);
  useEffect(() => {
    if (previous.current === resetKey) return;
    previous.current = resetKey;
    saved.current = 0;
    restored.current = true;
    target.current = 0;
    memory.set("scroll:" + scrollKey, 0);
    ref.current?.scrollToOffset({ offset: 0, animated: false });
  }, [resetKey]);
  useEffect(() => {
    if (!active || !ready || !viewport || !content || restored.current) return;
    if (content + 2 < Math.min(expectedHeight, saved.current + viewport))
      return;
    const offset = Math.min(saved.current, Math.max(0, content - viewport));
    target.current = offset;
    let timer: ReturnType<typeof setTimeout>,
      attempts = 0;
    const apply = () => {
      if (restored.current) return;
      ref.current?.scrollToOffset({ offset, animated: false });
      if (offset < 1) restored.current = true;
      else if (++attempts < 12) timer = setTimeout(apply, 120);
    };
    const frame = requestAnimationFrame(apply);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(timer);
    };
  }, [active, ready, viewport, content, expectedHeight]);
  return (
    <FlatList
      {...props}
      ref={ref}
      onLayout={(event) => setViewport(event.nativeEvent.layout.height)}
      onContentSizeChange={(_, height) => setContent(height)}
      onScrollBeginDrag={() => {
        restored.current = true;
      }}
      onScroll={(event) => {
        if (!active || !ready) return;
        const offset = Math.max(0, event.nativeEvent.contentOffset.y);
        if (!restored.current) {
          if (target.current === null || Math.abs(offset - target.current) > 2)
            return;
          restored.current = true;
        }
        memory.set("scroll:" + scrollKey, offset);
      }}
      scrollEventThrottle={32}
    />
  );
}
