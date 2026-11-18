import React, {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { Alert, AppState, DeviceEventEmitter } from "react-native";
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
  const { scope, report } = useApp(),
    [jobs, setJobs] = useState<DownloadJob[]>([]),
    [pulse, setPulse] = useState(0);
  const seen = useRef(new Set<string>()),
    alerting = useRef(false);
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
        void sync();
        return result;
      } catch (error: any) {
        Alert.alert("无法下载", error.message);
        throw error;
      }
    },
    [sync],
  );
  useEffect(() => {
    if (AppState.currentState !== "active" || alerting.current) return;
    const job = jobs.find(
      (value) =>
        (value.phase === "complete" || value.phase === "error") &&
        !value.seen &&
        !seen.current.has(value.id),
    );
    if (!job) return;
    alerting.current = true;
    seen.current.add(job.id);
    Native.acknowledgeDownload(job.id);
    const finish = () => {
      if (alerting.current) {
        alerting.current = false;
        setPulse((n) => n + 1);
      }
    };
    if (job.phase === "complete")
      Alert.alert(
        "下载完成",
        `${job.name}\n已保存到系统「下载 / Dazo」`,
        [
          { text: "知道了", onPress: finish },
          {
            text: "打开文件",
            onPress: () => {
              finish();
              Native.openDownload(job.id).catch((error) =>
                Alert.alert("无法打开", error.message),
              );
            },
          },
        ],
        { cancelable: true, onDismiss: finish },
      );
    else
      Alert.alert(
        "下载未完成",
        `${job.name}\n${job.error}`,
        [
          { text: "稍后", onPress: finish },
          {
            text: "重试",
            onPress: () => {
              finish();
              void download(job.path, job.name).catch(report);
            },
          },
        ],
        { cancelable: true, onDismiss: finish },
      );
  }, [jobs, pulse, download, report]);
  return (
    <Downloads.Provider value={{ jobs, download }}>
      {children}
    </Downloads.Provider>
  );
}
