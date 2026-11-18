import { useEffect, useMemo } from "react";
import { Native, Row } from "./core";
import { createPrefetcher } from "./prefetch";

const inFlight = new Map<string, Promise<Row>>();
export function loadMedia(scope: string, path: string, force = false) {
  const key = JSON.stringify([scope, path, force]);
  if (!inFlight.has(key)) {
    inFlight.set(
      key,
      Native.media(path, force)
        .then(JSON.parse)
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
