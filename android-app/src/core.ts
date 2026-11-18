import { NativeModules } from "react-native";
export {
  query,
  allowed,
  dateLabel,
  updateAge,
  bytesLabel,
  fileUrl,
  readingOffset,
  galleryRestoreOffset,
  galleryMediaUrl,
  upcomingImageUrls,
} from "./domain";

export type Row = Record<string, any>;
export type { Screen } from "./navigation";
export type Boot = {
  host: string;
  backupUrls: string[];
  userId: string;
  scope: string;
  me: Row | null;
  state: string;
};
export const Native = NativeModules.DazoBridge as {
  bootstrap(): Promise<string>;
  connectionInfo(): Promise<string>;
  updateState(): Promise<string>;
  checkUpdate(manual: boolean): void;
  setAutoUpdate(value: boolean): void;
  cancelUpdate(): void;
  installUpdate(): Promise<void>;
  login(
    host: string,
    backups: string,
    user: string,
    password: string,
  ): Promise<string>;
  themeMode(): Promise<string>;
  setThemeMode(value: string): void;
  setBackupUrls(value: string): Promise<string>;
  request(method: string, path: string, body: string): Promise<string>;
  media(path: string, force: boolean): Promise<string>;
  saveState(scope: string, json: string): void;
  logout(): Promise<void>;
  cacheSize(): Promise<number>;
  clearCache(): Promise<void>;
  pickUpload(folder: string): Promise<string>;
  cancelUpload(): void;
  pickAvatar(): Promise<void>;
  download(path: string, filename: string): Promise<void>;
  pickDateTime(initial: string, end: boolean): Promise<string>;
};
let values: Row = {},
  scope = "",
  timer: ReturnType<typeof setTimeout> | undefined;
const listeners = new Set<() => void>();
export const memory = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  init(boot: Boot) {
    if (timer) clearTimeout(timer);
    scope = boot.scope;
    try {
      values = JSON.parse(boot.state) || {};
    } catch {
      values = {};
    }
  },
  get<T>(key: string, fallback: T): T {
    return values[key] === undefined ? fallback : values[key];
  },
  set(key: string, value: any) {
    values[key] = value;
    listeners.forEach((listener) => listener());
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => this.flush(), 250);
  },
  flush() {
    if (scope) Native.saveState(scope, JSON.stringify(values));
  },
};
