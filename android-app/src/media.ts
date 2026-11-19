import { useEffect, useMemo } from "react";
import { Native, Row } from "./core";
import { createPrefetcher } from "./prefetch";
import { createMediaCache } from "./media-cache";

const inFlight = new Map<string, Promise<Row>>();
const loaded = createMediaCache<Row>();
export const loadedMedia = (scope: string, path?: string) =>
  loaded.get(scope, path);
export const clearLoadedMedia = () => loaded.clear();
export function loadMedia(scope: string, path: string, force = false) {
  const key = JSON.stringify([scope, path, force]);
  if (!inFlight.has(key)) {
    inFlight.set(
      key,
      Native.media(path, force)
        .then(JSON.parse)
        .then((result: Row) => {
          loaded.set(scope, path, result);
          return result;
        })
        .finally(() => inFlight.delete(key)),
    );
  }
  return inFlight.get(key)!;
}
export function useMediaPrefetch(scope: string, urls: string[]) {
  const worker = useMemo(
    () => createPrefetcher((url) => loadMedia(scope, url)),
    [scope],
  );
  const windowKey = JSON.stringify(urls);
  useEffect(() => {
    worker.replace(JSON.parse(windowKey));
  }, [worker, windowKey]);
  useEffect(() => () => worker.stop(), [worker]);
}
