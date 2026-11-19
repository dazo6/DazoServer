/** Metadata only: pixels and files remain owned by the bounded native caches. */
export function createMediaCache<T>(limit = 128) {
  const entries = new Map<string, T>();
  const key = (scope: string, path: string) => JSON.stringify([scope, path]);
  return {
    get(scope: string, path?: string) {
      if (!path) return undefined;
      const id = key(scope, path),
        value = entries.get(id);
      if (value !== undefined) {
        entries.delete(id);
        entries.set(id, value);
      }
      return value;
    },
    set(scope: string, path: string, value: T) {
      const id = key(scope, path);
      entries.delete(id);
      entries.set(id, value);
      while (entries.size > limit) entries.delete(entries.keys().next().value!);
    },
    clear() {
      entries.clear();
    },
  };
}
