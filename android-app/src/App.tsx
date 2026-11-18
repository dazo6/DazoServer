import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  ActivityIndicator,
  Alert,
  AppState,
  BackHandler,
  Keyboard,
  KeyboardAvoidingView,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  View,
} from "react-native";
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from "react-native-safe-area-context";
import { allowed, Boot, memory, Native, query, Row, Screen } from "./core";
import { AppContext, useSaved } from "./context";
import { Button, C, Icon, IconButton, Input, Media, S, Txt } from "./ui";
import { Artists, FreeGalleries, Home } from "./screens/Library";
import { Gallery } from "./screens/Gallery";
import {
  Account,
  History,
  More,
  Settings,
  Tasks,
  Users,
} from "./screens/Management";

export default function App() {
  const [boot, setBoot] = useState<Boot | null>(null),
    [failure, setFailure] = useState("");
  const bootstrap = useCallback(() => {
    setFailure("");
    Native.bootstrap()
      .then((raw) => {
        const value = JSON.parse(raw);
        memory.init(value);
        setBoot(value);
      })
      .catch((e) => setFailure(e.message));
  }, []);
  useEffect(bootstrap, [bootstrap]);
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      {boot?.me ? (
        <Authenticated
          key={boot.scope}
          initial={boot}
          signedOut={() => setBoot({ ...boot, me: null })}
        />
      ) : boot ? (
        <Login
          initial={boot}
          onLogin={(value) => {
            memory.init(value);
            setBoot(value);
          }}
        />
      ) : (
        <View
          style={{
            flex: 1,
            backgroundColor: C.bg,
            justifyContent: "center",
            alignItems: "center",
            gap: 12,
          }}
        >
          {failure ? (
            <>
              <Txt muted>连接暂时不可用</Txt>
              <Button title="重新连接" onPress={bootstrap} />
              <Button
                title="切换服务器"
                quiet
                onPress={() => {
                  Native.connectionInfo()
                    .then((raw) => setBoot(JSON.parse(raw)))
                    .catch((e) => setFailure(e.message));
                }}
              />
            </>
          ) : (
            <>
              <ActivityIndicator color={C.blue} />
              <Txt muted>正在连接…</Txt>
            </>
          )}
        </View>
      )}
    </SafeAreaProvider>
  );
}
function Login({
  initial,
  onLogin,
}: {
  initial: Boot;
  onLogin(boot: Boot): void;
}) {
  const inset = useSafeAreaInsets(),
    [host, setHost] = useState(initial.host),
    [user, setUser] = useState(initial.userId),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const login = async () => {
    Keyboard.dismiss();
    if (!host.trim() || !user.trim() || !password) {
      setError("请填写服务器地址、用户 ID 和密码");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const boot = JSON.parse(
        await Native.login(host.trim(), user.trim(), password),
      );
      setPassword("");
      onLogin(boot);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: C.bg }}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          padding: 24,
          paddingTop: inset.top + 56,
          flexGrow: 1,
        }}
      >
        <View style={[S.row, { gap: 12, marginBottom: 44 }]}>
          <View
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              backgroundColor: C.blue,
              alignItems: "center",
              justifyContent: "center",
              overflow: "hidden",
            }}
          >
            <View
              style={{
                position: "absolute",
                width: 46,
                height: 46,
                backgroundColor: C.cyan,
                transform: [{ rotate: "45deg" }],
                right: -26,
                top: -20,
              }}
            />
            <Txt size={27} bold style={{ color: "white" }}>
              F
            </Txt>
          </View>
          <View>
            <Txt size={20} bold>
              Fanbox Admin
            </Txt>
            <Txt size={12} muted>
              Crawler Console
            </Txt>
          </View>
        </View>
        <Txt size={30} bold>
          连接你的图库
        </Txt>
        <Txt muted style={{ marginTop: 8, marginBottom: 28 }}>
          使用现有服务账户登录
        </Txt>
        <Input
          label="服务器地址"
          placeholder="https://example.com"
          value={host}
          onChange={setHost}
        />
        <Input label="用户 ID" value={user} onChange={setUser} />
        <Input label="密码" secure value={password} onChange={setPassword} />
        {!!error && (
          <Txt size={13} style={{ color: C.red, marginBottom: 16 }}>
            {error}
          </Txt>
        )}
        <View style={{ marginTop: 12 }}>
          <Button title="登录" busy={busy} onPress={() => void login()} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
const tabs: { name: Screen["name"]; title: string; icon: string }[] = [
  { name: "home", title: "首页", icon: "home" },
  { name: "artists", title: "画师管理", icon: "artist" },
  { name: "free", title: "自由画廊", icon: "gallery" },
  { name: "more", title: "更多", icon: "more" },
];
const titles: Record<Screen["name"], string> = {
  home: "仪表盘",
  artists: "画师管理",
  free: "自由画廊",
  more: "更多",
  gallery: "图库",
  history: "历史爬取记录",
  tasks: "定时任务管理",
  users: "用户管理",
  account: "我的账户",
  storage: "设置",
};
function Authenticated({
  initial,
  signedOut,
}: {
  initial: Boot;
  signedOut(): void;
}) {
  const inset = useSafeAreaInsets(),
    [me, setMe] = useState(initial.me!),
    [avatarVersion, setAvatarVersion] = useState(Date.now()),
    [navigation, setNavigation] = useSaved<{
      tab: Screen["name"];
      stack: Screen[];
    }>("navigation", { tab: "home", stack: [] });
  const [toast, setToast] = useState(""),
    toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [originalImages, setOriginalImages] = useSaved(
    "settings:originalImages",
    false,
  );
  const current = navigation.stack.at(-1) || { name: navigation.tab };
  const notify = useCallback((message: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast(message);
    toastTimer.current = setTimeout(() => setToast(""), 3500);
  }, []);
  const report = useCallback(
    (error: any) => {
      if (error?.code === "CANCELLED") return;
      if (error?.code === "SESSION") {
        memory.flush();
        signedOut();
        return;
      }
      notify(error?.message || "请求失败，请稍后下拉重试");
    },
    [signedOut, notify],
  );
  const request = useCallback(
    async <T,>(method: string, path: string, body?: Row): Promise<T> => {
      try {
        return JSON.parse(
          await Native.request(method, path, body ? JSON.stringify(body) : ""),
        );
      } catch (e) {
        if ((e as any).code === "SESSION") report(e);
        throw e;
      }
    },
    [report],
  );
  const back = useCallback(() => {
    Keyboard.dismiss();
    if (navigation.stack.length) {
      setNavigation({ ...navigation, stack: navigation.stack.slice(0, -1) });
      return true;
    }
    if (navigation.tab !== "home") {
      setNavigation({ tab: "home", stack: [] });
      return true;
    }
    return false;
  }, [navigation]);
  const navigate = useCallback(
    (screen: Screen) => {
      Keyboard.dismiss();
      if (screen.name === "tasks" && !me.isSuperAdmin) return;
      if (screen.name === "users" && !allowed(me, "read", "op:user.list"))
        return;
      setNavigation({ ...navigation, stack: [...navigation.stack, screen] });
    },
    [navigation, me],
  );
  useEffect(() => {
    const backSubscription = BackHandler.addEventListener(
      "hardwareBackPress",
      back,
    );
    return () => backSubscription.remove();
  }, [back]);
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      if (state !== "active") memory.flush();
    });
    return () => {
      subscription.remove();
      memory.flush();
    };
  }, []);
  useEffect(() => {
    if (
      (current.name === "tasks" && !me.isSuperAdmin) ||
      (current.name === "users" && !allowed(me, "read", "op:user.list"))
    ) {
      setNavigation({ tab: "more", stack: [] });
    }
  }, [me, current.name]);
  const context = useMemo(
    () => ({
      me,
      host: initial.host,
      scope: initial.scope,
      avatarVersion,
      originalImages,
      setOriginalImages,
      navigate,
      back: () => {
        back();
      },
      setMe,
      avatarChanged: () => setAvatarVersion(Date.now()),
      request,
      report,
      toast: notify,
      logout: async () => {
        memory.flush();
        await Native.logout();
        signedOut();
      },
    }),
    [
      me,
      initial,
      avatarVersion,
      originalImages,
      setOriginalImages,
      navigate,
      back,
      request,
      report,
      notify,
      signedOut,
    ],
  );
  let content: React.ReactNode;
  switch (current.name) {
    case "artists":
      content = <Artists />;
      break;
    case "free":
      content = <FreeGalleries />;
      break;
    case "gallery":
      content = (
        <Gallery
          key={current.path}
          path={current.path || ""}
          artistId={current.artistId}
        />
      );
      break;
    case "history":
      content = <History />;
      break;
    case "tasks":
      content = <Tasks />;
      break;
    case "users":
      content = <Users />;
      break;
    case "account":
      content = <Account />;
      break;
    case "storage":
      content = <Settings />;
      break;
    case "more":
      content = <More />;
      break;
    default:
      content = <Home />;
  }
  return (
    <AppContext.Provider value={context}>
      <View style={{ flex: 1, backgroundColor: C.bg, paddingTop: inset.top }}>
        <View
          style={[
            S.row,
            {
              height: 48,
              paddingHorizontal: 6,
              backgroundColor: "white",
              borderBottomWidth: 1,
              borderColor: C.line,
            },
          ]}
        >
          {navigation.stack.length > 0 && (
            <IconButton name="back" label="返回" onPress={() => back()} />
          )}
          <Txt size={22} bold>
            {titles[current.name]}
          </Txt>
          {current.path && (
            <Pressable
              accessibilityLabel="查看完整路径"
              onPress={() => Alert.alert("路径", current.path!)}
              style={{ flex: 1, marginLeft: 6 }}
            >
              <Txt size={11} muted lines={1}>
                / {current.path.replace(/\\/g, "/")}
              </Txt>
            </Pressable>
          )}
          {!current.path && <View style={{ flex: 1 }} />}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="我的账户"
            onPress={() =>
              current.name !== "account" && navigate({ name: "account" })
            }
            style={{ marginLeft: 8, marginRight: 4 }}
          >
            <Media
              avatar
              path={query("/api/users/avatar", {
                userId: me.userId,
                t: avatarVersion,
              })}
              style={{ width: 36, height: 36, borderRadius: 18 }}
            />
          </Pressable>
        </View>
        <View
          key={current.name + ":" + (current.path || "")}
          style={{ flex: 1 }}
        >
          {content}
        </View>
        {navigation.stack.length === 0 ? (
          <View
            style={[
              S.row,
              {
                backgroundColor: "white",
                paddingBottom: inset.bottom,
                borderTopWidth: 1,
                borderColor: C.line,
                paddingHorizontal: 4,
              },
            ]}
          >
            {tabs.map((tab) => (
              <Pressable
                key={tab.name}
                accessibilityRole="tab"
                accessibilityState={{ selected: navigation.tab === tab.name }}
                onPress={() => {
                  Keyboard.dismiss();
                  setNavigation({ tab: tab.name, stack: [] });
                }}
                style={{
                  flex: 1,
                  alignItems: "center",
                  paddingVertical: 4,
                  gap: 2,
                }}
              >
                <View
                  style={{
                    width: 54,
                    height: 30,
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor:
                      navigation.tab === tab.name ? C.pale : "transparent",
                    borderRadius: 14,
                  }}
                >
                  <Icon
                    name={tab.icon}
                    size={27}
                    color={navigation.tab === tab.name ? C.blue : C.muted}
                  />
                </View>
                <Txt
                  size={11}
                  bold={navigation.tab === tab.name}
                  style={{
                    color: navigation.tab === tab.name ? C.blue : C.muted,
                  }}
                >
                  {tab.title}
                </Txt>
              </Pressable>
            ))}
          </View>
        ) : (
          <View style={{ height: inset.bottom, backgroundColor: C.bg }} />
        )}
        {!!toast && (
          <Pressable
            onPress={() => setToast("")}
            style={{
              position: "absolute",
              bottom: inset.bottom + (navigation.stack.length ? 18 : 86),
              left: 24,
              right: 24,
              backgroundColor: "#172033ed",
              padding: 14,
              borderRadius: 12,
              elevation: 8,
            }}
          >
            <Txt size={13} style={{ color: "white" }}>
              {toast}
            </Txt>
          </Pressable>
        )}
      </View>
    </AppContext.Provider>
  );
}
