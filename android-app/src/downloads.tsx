import React, {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { AppState, DeviceEventEmitter, ToastAndroid } from "react-native";
import { Native } from "./core";
import { useApp } from "./context";

export type DownloadJob = {
  id: string;
  scope: string;
  path: string;
  name: string;
  phase: string;
  bytes: number;
  total: number;
  seen: boolean;
  error?: string;
  location: string;
};
const Downloads = createContext({
  jobs: [] as DownloadJob[],
  download: async (_path: string, _name: string): Promise<DownloadJob> => null!,
});
export const useDownloads = () => useContext(Downloads);
export function DownloadProvider({ children }: { children: ReactNode }) {
  const { scope } = useApp();
  const [jobs, setJobs] = useState<DownloadJob[]>([]),
    [pulse, setPulse] = useState(0);
  const seen = useRef(new Set<string>());
  const sync = useCallback(
    () =>
      Native.downloadsState()
        .then((raw) =>
          setJobs(
            JSON.parse(raw).filter((job: DownloadJob) => job.scope === scope),
          ),
        )
        .catch(() => {}),
    [scope],
  );
  useEffect(() => {
    void sync();
    const event = DeviceEventEmitter.addListener("DazoDownloadState", (raw) =>
      setJobs(
        JSON.parse(raw).filter((job: DownloadJob) => job.scope === scope),
      ),
    );
    const lifecycle = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        void sync();
        setPulse((n) => n + 1);
      }
    });
    return () => {
      event.remove();
      lifecycle.remove();
    };
  }, [scope, sync]);
  const download = useCallback(
    async (path: string, name: string) => {
      try {
        const result = JSON.parse(await Native.download(path, name));
        ToastAndroid.show("已开始下载", ToastAndroid.SHORT);
        void sync();
        return result;
      } catch (error: any) {
        ToastAndroid.show(error.message || "暂时无法下载", ToastAndroid.SHORT);
        throw error;
      }
    },
    [sync],
  );
  useEffect(() => {
    if (AppState.currentState !== "active") return;
    const finished = jobs.filter(
      (job) =>
        (job.phase === "complete" || job.phase === "error") &&
        !job.seen &&
        !seen.current.has(job.id),
    );
    if (!finished.length) return;
    for (const job of finished) {
      seen.current.add(job.id);
      Native.acknowledgeDownload(job.id);
    }
    const saved = finished.filter((job) => job.phase === "complete").length,
      failed = finished.length - saved;
    const locations = new Set(
      finished
        .filter((job) => job.phase === "complete")
        .map((job) => job.location),
    );
    const location = locations.size === 1 ? [...locations][0] : "";
    const message =
      saved && failed
        ? `${saved} 个文件已保存，${failed} 个下载失败`
        : failed
          ? "下载失败，请检查网络后重试"
          : saved > 1
            ? `${saved} 个文件已保存${location ? "到" + location : ""}`
            : `已保存到${location || "下载 / DazoGallery"}`;
    ToastAndroid.show(message, ToastAndroid.SHORT);
  }, [jobs, pulse]);
  return (
    <Downloads.Provider value={{ jobs, download }}>
      {children}
    </Downloads.Provider>
  );
}
