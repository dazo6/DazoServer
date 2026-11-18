import React, {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { StatusBar, StyleSheet, useColorScheme } from "react-native";
import { Native } from "./core";

export type ThemeMode = "system" | "dark" | "light";
const light = {
  blue: "#218ec5",
  cyan: "#55bfda",
  ink: "#172033",
  muted: "#687386",
  bg: "#f1f5f9",
  surface: "#ffffff",
  line: "#e3eaf1",
  pale: "#eaf6fb",
  red: "#c43d4b",
  dangerBg: "#fff1f3",
  ripple: "#d5eaf5",
  onBlue: "#ffffff",
};
const dark: typeof light = {
  blue: "#65c4ee",
  cyan: "#429db7",
  ink: "#e5edf6",
  muted: "#9baabd",
  bg: "#10161f",
  surface: "#19222e",
  line: "#2b394a",
  pale: "#203748",
  red: "#ff8c9b",
  dangerBg: "#422934",
  ripple: "#304f65",
  onBlue: "#10202b",
};
function styles(C: typeof light) {
  return StyleSheet.create({
    row: { flexDirection: "row", alignItems: "center" },
    page: { padding: 16, paddingBottom: 24 },
    card: {
      backgroundColor: C.surface,
      borderRadius: 16,
      borderWidth: 1,
      borderColor: C.line,
      padding: 16,
      marginBottom: 12,
      overflow: "hidden",
    },
    iconButton: {
      height: 44,
      width: 44,
      alignItems: "center",
      justifyContent: "center",
    },
    button: {
      minHeight: 48,
      borderRadius: 12,
      alignItems: "center",
      justifyContent: "center",
      paddingHorizontal: 18,
      marginBottom: 10,
    },
    input: {
      minHeight: 48,
      fontSize: 16,
      color: C.ink,
      borderWidth: 1,
      borderColor: C.line,
      backgroundColor: C.surface,
      borderRadius: 10,
      paddingHorizontal: 13,
      paddingVertical: 10,
    },
    search: {
      flexDirection: "row",
      alignItems: "center",
      backgroundColor: C.surface,
      borderWidth: 1,
      borderColor: C.line,
      borderRadius: 13,
      paddingLeft: 12,
      paddingRight: 4,
      minHeight: 50,
      marginBottom: 16,
    },
    sheet: {
      backgroundColor: C.surface,
      borderTopLeftRadius: 24,
      borderTopRightRadius: 24,
    },
  });
}
const Theme = createContext({
  C: light,
  S: styles(light),
  mode: "system" as ThemeMode,
  isDark: false,
  setMode: (_: ThemeMode) => {},
});
export const useUI = () => useContext(Theme);
export function ThemeProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme();
  const [mode, set] = useState<ThemeMode>("system");
  useEffect(() => {
    Native.themeMode()
      .then((value) => set(value as ThemeMode))
      .catch(() => {});
  }, []);
  const isDark = (mode === "system" ? system : mode) === "dark";
  const value = useMemo(() => {
    const C = isDark ? dark : light;
    return {
      C,
      S: styles(C),
      mode,
      isDark,
      setMode: (next: ThemeMode) => {
        set(next);
        Native.setThemeMode(next);
      },
    };
  }, [mode, isDark]);
  return (
    <Theme.Provider value={value}>
      <StatusBar barStyle={isDark ? "light-content" : "dark-content"} />
      {children}
    </Theme.Provider>
  );
}
