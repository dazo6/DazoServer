import React, {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useMemo,
  useState,
} from "react";
import {
  ActivityIndicator,
  AppState,
  DeviceEventEmitter,
  Pressable,
  ScrollView,
  ToastAndroid,
  View,
} from "react-native";
import { bytesLabel, Native } from "./core";
import { useApp } from "./context";
import { Icon, IconButton, Sheet, Txt } from "./ui";
import { useUI } from "./theme";
import { UploadJob, uploadPending, uploadSummary } from "./upload-state";

const Uploads = createContext({
  jobs: [] as UploadJob[],
  choose: async (_folder: string) => {},
});
const UploadActivity = createContext({
  jobs: [] as UploadJob[],
  choose: async (_folder: string) => {},
});
export const useUploads = () => useContext(Uploads);
export const useUploadActivity = () => useContext(UploadActivity);
export function UploadProvider({ children }: { children: ReactNode }) {
  const { scope, report } = useApp();
  const [jobs, setJobs] = useState<UploadJob[]>([]),
    [pulse, setPulse] = useState(0);
  const wasBusy = useRef(false);
  const sync = useCallback(
    () =>
      Native.uploadsState()
        .then((raw) =>
          setJobs(
            JSON.parse(raw).filter((job: UploadJob) => job.scope === scope),
          ),
        )
        .catch(() => {}),
    [scope],
  );
  useEffect(() => {
    void sync();
    const event = DeviceEventEmitter.addListener("DazoUploadState", (raw) =>
      setJobs(JSON.parse(raw).filter((job: UploadJob) => job.scope === scope)),
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
  useEffect(() => {
    const summary = uploadSummary(jobs);
    if (summary.active) {
      wasBusy.current = true;
      return;
    }
    if (AppState.currentState !== "active" || !wasBusy.current) return;
    wasBusy.current = false;
    if (summary.complete || summary.failed)
      ToastAndroid.show(
        summary.failed
          ? `${summary.complete} 个上传完成，${summary.failed} 个失败`
          : `${summary.complete} 个文件上传完成`,
        ToastAndroid.SHORT,
      );
  }, [jobs, pulse]);
  const choose = useCallback(
    async (folder: string) => {
      try {
        const ids: string[] = JSON.parse(await Native.pickUpload(folder));
        if (!ids.length)
          ToastAndroid.show("所选文件已在上传队列中", ToastAndroid.SHORT);
        await sync();
      } catch (error) {
        report(error);
      }
    },
    [report, sync],
  );
  const activityKey = jobs.map((job) => `${job.id}:${job.phase}`).join("|");
  const activity = useMemo(() => ({ jobs, choose }), [activityKey, choose]);
  return (
    <UploadActivity.Provider value={activity}>
      <Uploads.Provider value={{ jobs, choose }}>{children}</Uploads.Provider>
    </UploadActivity.Provider>
  );
}

export function UploadDock({ bottom }: { bottom: number }) {
  const { C, S } = useUI();
  const { jobs } = useUploads();
  const { report } = useApp();
  const [open, setOpen] = useState(false);
  const summary = uploadSummary(jobs);
  useEffect(() => {
    if (open || summary.active || summary.failed || !summary.visible.length)
      return;
    const timer = setTimeout(() => Native.dismissUploads(), 3500);
    return () => clearTimeout(timer);
  }, [open, summary.active, summary.failed, summary.visible.length]);
  if (!summary.visible.length) return null;
  const title = summary.active
    ? `正在上传 ${summary.complete}/${summary.visible.length}`
    : summary.failed
      ? `${summary.failed} 个上传失败`
      : summary.complete
        ? "上传完成"
        : "上传已取消";
  return (
    <>
      <View
        style={{
          position: "absolute",
          bottom,
          left: 8,
          right: 8,
          backgroundColor: C.surface,
          borderColor: C.line,
          borderWidth: 1,
          borderRadius: 12,
          elevation: 3,
          overflow: "hidden",
        }}
      >
        <View style={[S.row, { paddingLeft: 12 }]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="查看上传队列"
            onPress={() => setOpen(true)}
            style={[S.row, { flex: 1, paddingVertical: 8, gap: 10 }]}
          >
            <Icon
              name={summary.failed && !summary.active ? "info" : "upload"}
              size={23}
              color={summary.failed && !summary.active ? C.red : C.blue}
            />
            <View style={{ flex: 1 }}>
              <Txt size={12} bold>
                {title}
              </Txt>
              <Txt size={11} muted lines={1}>
                {summary.current?.name ||
                  (summary.complete
                    ? `${summary.complete} 个文件已完成`
                    : `${summary.cancelled} 个文件已取消`)}
              </Txt>
            </View>
            {summary.active > 0 && (
              <Txt size={12} muted>
                {summary.percent}%
              </Txt>
            )}
          </Pressable>
          <IconButton
            name="close"
            label={summary.active ? "取消全部上传" : "关闭上传进度"}
            onPress={() => {
              if (summary.active) Native.cancelUpload();
              else Native.dismissUploads();
              setOpen(false);
            }}
          />
        </View>
        {summary.active > 0 && (
          <View style={{ height: 3, backgroundColor: C.pale }}>
            <View
              style={{
                height: 3,
                width: `${summary.percent}%`,
                backgroundColor: C.blue,
              }}
            />
          </View>
        )}
      </View>
      <Sheet visible={open} title="上传队列" onClose={() => setOpen(false)}>
        <ScrollView style={{ maxHeight: 440 }}>
          {summary.visible.map((job) => (
            <View
              key={job.id}
              style={{
                paddingVertical: 12,
                borderBottomWidth: 1,
                borderColor: C.line,
              }}
            >
              <View style={[S.row, { gap: 10 }]}>
                <View style={{ flex: 1 }}>
                  <Txt size={14} lines={1}>
                    {job.name}
                  </Txt>
                  <Txt muted size={11} lines={1}>
                    {job.folder}
                  </Txt>
                  <Txt
                    size={12}
                    muted
                    style={job.phase === "error" ? { color: C.red } : undefined}
                  >
                    {job.phase === "error"
                      ? job.error || "上传失败"
                      : job.phase === "complete"
                        ? "已完成"
                        : job.phase === "cancelled"
                          ? "已取消"
                          : job.phase === "preparing"
                            ? "正在读取文件…"
                            : job.phase === "queued"
                              ? "等待上传"
                              : `${bytesLabel(job.bytes)} / ${bytesLabel(job.total)}`}
                  </Txt>
                </View>
                {job.stopping ? (
                  <ActivityIndicator color={C.blue} />
                ) : uploadPending(job) ? (
                  <IconButton
                    name="close"
                    label={`取消上传 ${job.name}`}
                    onPress={() => Native.cancelUploadFile(job.id)}
                  />
                ) : job.phase === "error" || job.phase === "cancelled" ? (
                  <IconButton
                    name="reset"
                    label={`重试上传 ${job.name}`}
                    onPress={() =>
                      void Native.retryUpload(job.id).catch(report)
                    }
                  />
                ) : (
                  <Icon name="check" size={22} />
                )}
              </View>
              {job.phase === "uploading" && (
                <View
                  style={{ height: 3, marginTop: 8, backgroundColor: C.pale }}
                >
                  <View
                    style={{
                      height: 3,
                      backgroundColor: C.blue,
                      width: `${job.total ? Math.min(99, (job.bytes * 100) / job.total) : 0}%`,
                    }}
                  />
                </View>
              )}
            </View>
          ))}
        </ScrollView>
      </Sheet>
    </>
  );
}
