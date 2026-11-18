import { NativeModules } from "react-native";
export {
  query,
  allowed,
  dateLabel,
  bytesLabel,
  fileUrl,
  readingOffset,
  galleryMediaUrl,
  upcomingImageUrls,
} from "./domain";

export type Row = Record<string, any>;
export type Screen = {
  name:
    | "home"
    | "artists"
    | "free"
    | "more"
    | "gallery"
    | "history"
    | "tasks"
    | "users"
    | "account"
    | "storage";
  path?: string;
  artistId?: string;
};
export type Boot = {
  host: string;
  userId: string;
  scope: string;
  me: Row | null;
  state: string;
};
export const Native = NativeModules.DazoBridge as {
  bootstrap(): Promise<string>;
  connectionInfo(): Promise<string>;
  login(host: string, user: string, password: string): Promise<string>;
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
export const memory = {
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
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => this.flush(), 250);
  },
  flush() {
    if (scope) Native.saveState(scope, JSON.stringify(values));
  },
};
