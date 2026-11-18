/** One background read at a time. Replacing the window drops obsolete queued reads. */
export function createPrefetcher(load: (url: string) => Promise<unknown>) {
  let pending: string[] = [];
  let desired = new Set<string>();
  let completed = new Set<string>();
  let active: string | undefined;
  let running = false;
  let stopped = false;
  async function drain() {
    if (running || stopped) return;
    running = true;
    try {
      while (pending.length && !stopped) {
        const url = pending.shift()!;
        active = url;
        try {
          await load(url);
          if (desired.has(url)) completed.add(url);
        } catch {
          // A failed speculative read never interrupts browsing.
        } finally {
          active = undefined;
        }
      }
    } finally {
      running = false;
    }
  }
  return {
    replace(urls: string[]) {
      if (stopped) return;
      desired = new Set(urls.slice(0, 10));
      completed = new Set([...completed].filter((url) => desired.has(url)));
      pending = [...desired].filter(
        (url) => url !== active && !completed.has(url),
      );
      void drain();
    },
    stop() {
      stopped = true;
      pending = [];
      desired.clear();
      completed.clear();
    },
  };
}
