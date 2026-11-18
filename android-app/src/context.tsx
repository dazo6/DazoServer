import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { memory, Native, Row, Screen } from "./core";

export type AppContextValue = {
  me: Row;
  host: string;
  backupUrls: string[];
  setBackupUrls(urls: string[]): Promise<void>;
  scope: string;
  avatarVersion: number;
  originalImages: boolean;
  setOriginalImages(value: boolean): void;
  navigate(screen: Screen): void;
  back(): void;
  setMe(me: Row): void;
  avatarChanged(): void;
  request<T = any>(method: string, path: string, body?: Row): Promise<T>;
  report(error: any): void;
  toast(message: string): void;
  logout(): Promise<void>;
};
export const AppContext = createContext<AppContextValue>(null!);
export const useApp = () => useContext(AppContext);
export const ScreenActive = createContext(true);
export const NavigationTab = createContext("home");
export const useScreenActive = () => useContext(ScreenActive);
export function useClock() {
  const active = useScreenActive(),
    [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, [active]);
  return now;
}
export function useSaved<T>(
  key: string,
  fallback: T,
): [T, (value: T | ((old: T) => T)) => void] {
  const initial = useRef(fallback);
  const value = useSyncExternalStore(memory.subscribe, () =>
    memory.get(key, initial.current),
  );
  const set = useCallback(
    (next: T | ((old: T) => T)) => {
      const old = memory.get(key, initial.current);
      const result =
        typeof next === "function" ? (next as (v: T) => T)(old) : next;
      memory.set(key, result);
    },
    [key],
  );
  return [value, set];
}
export function useDebounced<T>(value: T, ms = 400) {
  const [result, set] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => set(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return result;
}
export function useRemote<T>(loader: () => Promise<T>, deps: unknown[]) {
  const { report } = useApp();
  const active = useScreenActive();
  const token = useRef(0);
  const [data, setData] = useState<T | null>(null),
    [loading, setLoading] = useState(true),
    [refreshing, setRefreshing] = useState(false),
    [revision, setRevision] = useState(0);
  const load = useCallback(async (pull = false) => {
    const current = ++token.current;
    if (pull) setRefreshing(true);
    else setLoading(true);
    try {
      const value = await loader();
      if (current === token.current) {
        setData(value);
        if (pull) setRevision((x) => x + 1);
      }
    } catch (error) {
      if (current === token.current) report(error);
    } finally {
      if (current === token.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, deps);
  useEffect(() => {
    if (active) void load();
    return () => {
      token.current++;
    };
  }, [load, active]);
  return {
    data,
    loading,
    refreshing,
    revision,
    reload: () => load(false),
    refresh: () => load(true),
  };
}
