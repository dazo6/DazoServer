export type PagedResult<T> = { records: T[]; total: number };
export function createPagedFeed<T>(
  load: (page: number) => Promise<PagedResult<T>>,
  identity: (item: T) => string,
) {
  let state = {
    records: [] as T[],
    total: 0,
    page: 0,
    loading: true,
    refreshing: false,
    loadingMore: false,
    hasMore: true,
    error: null as any,
    revision: 0,
  };
  let epoch = 0,
    busy = false;
  const listeners = new Set<() => void>();
  const publish = (next: Partial<typeof state>) => {
    state = { ...state, ...next };
    listeners.forEach((listener) => listener());
  };
  const merge = (before: T[], incoming: T[]) =>
    Array.from(
      new Map(
        [...before, ...incoming].map((item) => [identity(item), item]),
      ).values(),
    );
  const refresh = async (pages = Math.max(1, state.page), pull = true) => {
    const token = ++epoch;
    busy = true;
    publish({
      loading: !state.records.length,
      refreshing: pull,
      loadingMore: false,
      error: null,
    });
    try {
      let records: T[] = [],
        total = 0,
        page = 0,
        hasMore = true;
      while (page < pages && hasMore) {
        const result = await load(page + 1);
        if (token !== epoch) return;
        records = merge(records, result.records);
        total = result.total;
        page++;
        hasMore = result.records.length > 0 && records.length < total;
      }
      publish({
        records,
        total,
        page,
        hasMore,
        revision: state.revision + (pull ? 1 : 0),
      });
    } catch (error) {
      if (token === epoch) publish({ error });
    } finally {
      if (token === epoch) {
        busy = false;
        publish({ loading: false, refreshing: false });
      }
    }
  };
  const next = async () => {
    if (busy || !state.page || !state.hasMore) return;
    const token = epoch;
    busy = true;
    publish({ loadingMore: true, error: null });
    try {
      const result = await load(state.page + 1);
      if (token !== epoch) return;
      const records = merge(state.records, result.records);
      publish({
        records,
        total: result.total,
        page: state.page + 1,
        hasMore: result.records.length > 0 && records.length < result.total,
      });
    } catch (error) {
      if (token === epoch) publish({ error });
    } finally {
      if (token === epoch) {
        busy = false;
        publish({ loadingMore: false });
      }
    }
  };
  return {
    snapshot: () => state,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    ensure: (pages: number) =>
      !busy && !state.page ? refresh(pages, false) : Promise.resolve(),
    refresh: () => refresh(),
    next,
    cancel: () => {
      epoch++;
      busy = false;
      publish({ loadingMore: false, refreshing: false, loading: !state.page });
    },
  };
}
