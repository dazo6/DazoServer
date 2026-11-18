import React, {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  Alert,
  AppState,
  DeviceEventEmitter,
  Pressable,
  Switch,
  View,
} from "react-native";
import { bytesLabel, Native } from "./core";
import { Button, C, Card, Divider, Icon, S, Section, Sheet, Txt } from "./ui";

type UpdateState = {
  phase: string;
  versionCode: number;
  versionName: string;
  autoCheck: boolean;
  lastCheckedAt: number;
  message: string;
  error: string;
  downloadedBytes: number;
  totalBytes: number;
  candidate?: { versionCode?: number; versionName?: string; size?: number };
};
const initial: UpdateState = {
  phase: "idle",
  versionCode: 0,
  versionName: "",
  autoCheck: true,
  lastCheckedAt: 0,
  message: "",
  error: "",
  downloadedBytes: 0,
  totalBytes: 0,
};
const Updates = createContext({
  state: initial,
  showReady: () => {},
  install: async () => {},
});
export function UpdateProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState(initial),
    [ready, setReady] = useState(false);
  const prompted = useRef(0);
  useEffect(() => {
    const sync = () =>
      Native.updateState()
        .then((raw) => setState(JSON.parse(raw)))
        .catch(() => {});
    const listener = DeviceEventEmitter.addListener("DazoUpdateState", (raw) =>
      setState(JSON.parse(raw)),
    );
    void sync().then(() => Native.checkUpdate(false));
    const lifecycle = AppState.addEventListener("change", (value) => {
      if (value === "active") void sync().then(() => Native.checkUpdate(false));
    });
    return () => {
      listener.remove();
      lifecycle.remove();
    };
  }, []);
  useEffect(() => {
    const code = state.candidate?.versionCode || 0;
    if (
      state.phase === "ready" &&
      code > prompted.current &&
      AppState.currentState === "active"
    ) {
      prompted.current = code;
      setReady(true);
    }
  }, [state]);
  const install = async () => {
    setReady(false);
    try {
      await Native.installUpdate();
    } catch (error: any) {
      Alert.alert("暂时无法安装", error.message);
    }
  };
  return (
    <Updates.Provider
      value={{ state, showReady: () => setReady(true), install }}
    >
      {children}
      <Sheet
        visible={ready && state.phase === "ready"}
        title="新版已准备好"
        onClose={() => setReady(false)}
      >
        <View style={{ gap: 10, marginBottom: 20 }}>
          <Txt size={23} bold>
            {state.candidate?.versionName}
          </Txt>
          <Txt muted size={13}>
            下载和校验已完成，覆盖安装会保留登录与阅读位置。
          </Txt>
          <Txt muted size={12}>
            首次更新需允许安装此来源，随后由 Android 确认安装。
          </Txt>
        </View>
        <Button title="安装更新" onPress={() => void install()} />
        <Button title="稍后" quiet onPress={() => setReady(false)} />
      </Sheet>
    </Updates.Provider>
  );
}
export function UpdateSection() {
  const { state, install } = useContext(Updates);
  const downloading = state.phase === "downloading",
    checking = state.phase === "checking" || state.phase === "verifying";
  const progress =
    state.totalBytes > 0
      ? Math.min(
          100,
          Math.round((state.downloadedBytes * 100) / state.totalBytes),
        )
      : 0;
  return (
    <>
      <Section
        title="应用更新"
        detail={
          state.versionName ? `当前版本 ${state.versionName}` : "正在读取版本…"
        }
      />
      <Card style={{ paddingVertical: 0 }}>
        <View style={[S.row, { gap: 12, minHeight: 64 }]}>
          <Icon name="download" />
          <View style={{ flex: 1, paddingVertical: 10 }}>
            <Txt>自动检查更新</Txt>
            <Txt muted size={12}>
              打开应用时检查，有新版自动下载
            </Txt>
          </View>
          <Switch
            accessibilityLabel="自动检查更新"
            value={state.autoCheck}
            onValueChange={Native.setAutoUpdate}
            trackColor={{ false: "#cbd5e1", true: C.cyan }}
            thumbColor={state.autoCheck ? C.blue : "#f8fafc"}
          />
        </View>
        <Divider />
        <View style={{ paddingVertical: 14, gap: 12 }}>
          <Txt
            size={13}
            muted
            style={state.error ? { color: C.red } : undefined}
          >
            {state.error ||
              state.message ||
              "从项目的 Android 更新版本获取安装包"}
          </Txt>
          {(downloading || state.phase === "ready") && (
            <Txt size={13} bold>
              {state.candidate?.versionName}　{bytesLabel(state.totalBytes)}
            </Txt>
          )}
          {downloading && (
            <>
              <View
                style={{
                  height: 6,
                  borderRadius: 3,
                  backgroundColor: C.bg,
                  overflow: "hidden",
                }}
              >
                <View
                  style={{
                    height: 6,
                    width: `${progress}%`,
                    backgroundColor: C.blue,
                  }}
                />
              </View>
              <Txt muted size={12}>
                {progress}%　{bytesLabel(state.downloadedBytes)} /{" "}
                {bytesLabel(state.totalBytes)}
              </Txt>
            </>
          )}
          {state.phase === "ready" ? (
            <Button title="安装更新" onPress={() => void install()} />
          ) : downloading ? (
            <Button title="取消下载" quiet onPress={Native.cancelUpdate} />
          ) : (
            <Button
              title={checking ? state.message : "检查并下载更新"}
              busy={checking}
              onPress={() => Native.checkUpdate(true)}
            />
          )}
          {!!state.lastCheckedAt && (
            <Txt size={11} muted>
              上次检查　
              {new Date(state.lastCheckedAt).toLocaleString("zh-CN", {
                hour12: false,
              })}
            </Txt>
          )}
        </View>
      </Card>
      <View style={{ height: 12 }} />
    </>
  );
}
export function UpdateEntry() {
  const { state, showReady } = useContext(Updates);
  return (
    <Pressable
      accessibilityRole="button"
      onPress={() =>
        state.phase === "ready" ? showReady() : Native.checkUpdate(true)
      }
      style={{
        alignSelf: "center",
        minHeight: 44,
        justifyContent: "center",
        marginTop: 12,
      }}
    >
      <Txt size={12} muted>
        {state.versionName}　·　
        {state.phase === "ready"
          ? "安装更新"
          : state.phase === "downloading"
            ? "正在下载更新…"
            : state.phase === "checking"
              ? "正在检查更新…"
              : state.phase === "current"
                ? "已是最新版本"
                : state.phase === "error"
                  ? "检查失败，点此重试"
                  : "检查更新"}
      </Txt>
    </Pressable>
  );
}
