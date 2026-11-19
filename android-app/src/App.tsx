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
  DeviceEventEmitter,
  Keyboard,
  KeyboardAvoidingView,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from "react-native-safe-area-context";
import { allowed, Boot, memory, Native, query, Row, Screen } from "./core";
import {
  AppContext,
  NavigationTab,
  ScreenActive,
  useApp,
  useSaved,
} from "./context";
import { Button, Icon, IconButton, Input, Media, Txt } from "./ui";
import { ThemeProvider, useUI } from "./theme";
import {
  Navigation,
  normalizeNavigation,
  popScreen,
  pushScreen,
  selectTab,
  Tab,
  tabNames,
} from "./navigation";
import { Artists, FreeGalleries, Home } from "./screens/Library";
import { Gallery } from "./screens/Gallery";
import { UpdateEntry, UpdateProvider } from "./updates";
import {
  Account,
  History,
  More,
  Settings,
  Tasks,
  Users,
} from "./screens/Management";
import { ServerFields } from "./servers";
import { DownloadProvider } from "./downloads";

export default function App() {
  return (
    <ThemeProvider>
      <SafeAreaProvider>
        <UpdateProvider>
          <Root />
        </UpdateProvider>
      </SafeAreaProvider>
    </ThemeProvider>
  );
}
function Root() {
  const { C } = useUI();
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
  return boot?.me ? (
    <Authenticated
      key={boot.scope}
      initial={boot}
      signedOut={() => {
        Native.connectionInfo()
          .then((raw) => setBoot(JSON.parse(raw)))
          .catch(() => setBoot({ ...boot, me: null }));
      }}
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
          <UpdateEntry />
        </>
      ) : (
        <>
          <ActivityIndicator color={C.blue} />
          <Txt muted>正在连接…</Txt>
        </>
      )}
    </View>
  );
}
function Login({
  initial,
  onLogin,
}: {
  initial: Boot;
  onLogin(boot: Boot): void;
}) {
  const { C, S } = useUI(),
    inset = useSafeAreaInsets();
  const [host, setHost] = useState(initial.host),
    [backups, setBackups] = useState(initial.backupUrls || []),
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
        await Native.login(
          host.trim(),
          JSON.stringify(backups.filter((v) => v.trim())),
          user.trim(),
          password,
        ),
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
          paddingTop: inset.top + 40,
          paddingBottom: inset.bottom + 24,
          flexGrow: 1,
        }}
      >
        <View style={[S.row, { gap: 12, marginBottom: 32 }]}>
          <View
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              backgroundColor: C.blue,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Txt size={27} bold style={{ color: "white" }}>
              F
            </Txt>
          </View>
          <View>
            <Txt size={20} bold>
              DazoGallery
            </Txt>
            <Txt size={12} muted>
              Crawler Console
            </Txt>
          </View>
        </View>
        <Txt size={30} bold>
          连接你的图库
        </Txt>
        <Txt muted style={{ marginTop: 8, marginBottom: 24 }}>
          使用现有服务账户登录
        </Txt>
        <ServerFields
          host={host}
          setHost={setHost}
          backups={backups}
          setBackups={setBackups}
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
          <UpdateEntry />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
const tabs: { name: Tab; title: string; icon: string }[] = [
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
function Header({ screen, hasBack }: { screen: Screen; hasBack: boolean }) {
  const { C, S } = useUI(),
    { me, avatarVersion, navigate, back } = useApp();
  return (
    <View
      style={[
        S.row,
        {
          height: 48,
          paddingHorizontal: 6,
          backgroundColor: C.surface,
          borderBottomWidth: 1,
          borderColor: C.line,
        },
      ]}
    >
      {hasBack && <IconButton name="back" label="返回" onPress={back} />}
      <Txt size={22} bold>
        {titles[screen.name]}
      </Txt>
      {screen.path ? (
        <Pressable
          accessibilityLabel="查看完整路径"
          onPress={() => Alert.alert("路径", screen.path!)}
          style={{ flex: 1, marginLeft: 6 }}
        >
          <Txt size={11} muted lines={1}>
            / {screen.path.replace(/\\/g, "/")}
          </Txt>
        </Pressable>
      ) : (
        <View style={{ flex: 1 }} />
      )}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="我的账户"
        onPress={() =>
          screen.name !== "account" && navigate({ name: "account" })
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
  );
}
function Route({ screen, hasBack }: { screen: Screen; hasBack: boolean }) {
  const header = <Header screen={screen} hasBack={hasBack} />;
  if (screen.name === "gallery")
    return (
      <Gallery
        path={screen.path || ""}
        artistId={screen.artistId}
        header={header}
      />
    );
  const screens = {
    artists: Artists,
    free: FreeGalleries,
    history: History,
    tasks: Tasks,
    users: Users,
    account: Account,
    storage: Settings,
    more: More,
    home: Home,
  };
  const Content = screens[screen.name];
  return (
    <>
      {header}
      <Content />
    </>
  );
}
function Authenticated({
  initial,
  signedOut,
}: {
  initial: Boot;
  signedOut(): void;
}) {
  const { C, S } = useUI(),
    inset = useSafeAreaInsets();
  const [me, setMe] = useState(initial.me!),
    [avatarVersion, setAvatarVersion] = useState(Date.now());
  const [stored, setNavigation] = useSaved<Navigation>("navigation", {
    tab: "home",
    stacks: { home: [] },
  });
  const navigation = useMemo(() => normalizeNavigation(stored), [stored]),
    stack = navigation.stacks[navigation.tab] || [],
    current = stack.at(-1) || { name: navigation.tab };
  const [toast, setToast] = useState(""),
    toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [originalImages, setOriginalImages] = useSaved(
    "settings:originalImages",
    false,
  );
  const [autoplayVideos, setAutoplayVideos] = useSaved(
    "settings:autoplayVideos",
    false,
  );
  const [backupUrls, setBackups] = useState(initial.backupUrls || []);
  const [activeHost, setActiveHost] = useState(
    initial.activeHost || initial.host,
  );
  useEffect(() => {
    const changed = (raw: string) => {
      const value = JSON.parse(raw);
      if (value.host === initial.host) {
        setActiveHost(value.activeHost);
        setBackups(value.backupUrls);
      }
    };
    const listener = DeviceEventEmitter.addListener(
      "DazoConnectionState",
      changed,
    );
    Native.connectionState()
      .then(changed)
      .catch(() => {});
    return () => listener.remove();
  }, [initial.host]);
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
    if (!stack.length && navigation.tab === "home") return false;
    setNavigation(popScreen(navigation));
    return true;
  }, [navigation]);
  const navigate = useCallback(
    (screen: Screen) => {
      Keyboard.dismiss();
      if (
        (screen.name === "tasks" && !me.isSuperAdmin) ||
        (screen.name === "users" && !allowed(me, "read", "op:user.list"))
      )
        return;
      if (tabNames.includes(screen.name as Tab)) {
        const tab = screen.name as Tab;
        setNavigation({ tab, stacks: { ...navigation.stacks, [tab]: [] } });
      } else setNavigation(pushScreen(navigation, screen));
    },
    [navigation, me],
  );
  useEffect(() => {
    const listener = BackHandler.addEventListener("hardwareBackPress", back);
    return () => listener.remove();
  }, [back]);
  useEffect(() => {
    const listener = AppState.addEventListener("change", (state) => {
      if (state !== "active") memory.flush();
    });
    return () => {
      listener.remove();
      memory.flush();
    };
  }, []);
  useEffect(() => {
    if (
      (current.name === "tasks" && !me.isSuperAdmin) ||
      (current.name === "users" && !allowed(me, "read", "op:user.list"))
    )
      setNavigation(popScreen(navigation));
  }, [me, current.name]);
  const context = useMemo(
    () => ({
      me,
      host: initial.host,
      scope: initial.scope,
      backupUrls,
      activeHost,
      avatarVersion,
      originalImages,
      setOriginalImages,
      autoplayVideos,
      setAutoplayVideos,
      navigate,
      back: () => {
        back();
      },
      setMe,
      avatarChanged: () => setAvatarVersion(Date.now()),
      request,
      report,
      toast: notify,
      setBackupUrls: async (urls: string[]) => {
        setBackups(
          JSON.parse(await Native.setBackupUrls(JSON.stringify(urls))),
        );
      },
      logout: async () => {
        memory.flush();
        await Native.logout();
        signedOut();
      },
    }),
    [
      me,
      initial,
      backupUrls,
      activeHost,
      avatarVersion,
      originalImages,
      setOriginalImages,
      autoplayVideos,
      setAutoplayVideos,
      navigate,
      back,
      request,
      report,
      notify,
      signedOut,
    ],
  );
  return (
    <AppContext.Provider value={context}>
      <DownloadProvider>
        <View style={{ flex: 1, backgroundColor: C.bg, paddingTop: inset.top }}>
          <View style={{ flex: 1, overflow: "hidden" }}>
            {tabs
              .filter(
                (tab) =>
                  navigation.stacks[tab.name] !== undefined ||
                  tab.name === navigation.tab,
              )
              .flatMap((tab) => {
                const routes: Screen[] = [
                  { name: tab.name },
                  ...(navigation.stacks[tab.name] || []),
                ];
                return routes.map((screen, depth) => {
                  const active =
                    tab.name === navigation.tab && depth === routes.length - 1;
                  return (
                    <ScreenActive.Provider
                      key={`${tab.name}:${depth}:${screen.name}:${screen.path || ""}`}
                      value={active}
                    >
                      <NavigationTab.Provider value={tab.name}>
                        <View
                          pointerEvents={active ? "auto" : "none"}
                          accessibilityElementsHidden={!active}
                          importantForAccessibility={
                            active ? "auto" : "no-hide-descendants"
                          }
                          style={[
                            StyleSheet.absoluteFill,
                            {
                              backgroundColor: C.bg,
                              opacity: active ? 1 : 0,
                              zIndex: active ? 1 : 0,
                            },
                          ]}
                        >
                          <Route screen={screen} hasBack={depth > 0} />
                        </View>
                      </NavigationTab.Provider>
                    </ScreenActive.Provider>
                  );
                });
              })}
          </View>
          <View
            style={[
              S.row,
              {
                backgroundColor: C.surface,
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
                  setNavigation(selectTab(navigation, tab.name));
                }}
                style={{
                  flex: 1,
                  alignItems: "center",
                  paddingVertical: 3,
                  marginHorizontal: 3,
                  backgroundColor:
                    navigation.tab === tab.name ? C.pale : "transparent",
                  borderRadius: 16,
                }}
              >
                <View
                  style={{
                    width: 54,
                    height: 30,
                    alignItems: "center",
                    justifyContent: "center",
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
          {!!toast && (
            <Pressable
              onPress={() => setToast("")}
              style={{
                position: "absolute",
                bottom: inset.bottom + 72,
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
      </DownloadProvider>
    </AppContext.Provider>
  );
}
