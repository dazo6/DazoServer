import React, { ReactNode, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  View,
  ViewStyle,
  requireNativeComponent,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { memory, Native, Row } from "./core";
import { useApp } from "./context";
import { loadMedia } from "./media";

export const C = {
  blue: "#218ec5",
  cyan: "#55bfda",
  ink: "#172033",
  muted: "#687386",
  bg: "#f1f5f9",
  line: "#e3eaf1",
  pale: "#eaf6fb",
  red: "#c43d4b",
};
const NativeIcon = requireNativeComponent<{
  kind: string;
  color: string;
  style: StyleProp<ViewStyle>;
}>("DazoIcon");
export function Icon({
  name,
  size = 26,
  color = C.blue,
}: {
  name: string;
  size?: number;
  color?: string;
}) {
  return (
    <NativeIcon
      kind={name}
      color={color}
      style={{ width: size, height: size }}
    />
  );
}
export function IconButton({
  name,
  label,
  onPress,
  filled = false,
  disabled = false,
}: {
  name: string;
  label: string;
  onPress(): void;
  filled?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={onPress}
      android_ripple={{ color: "#d5eaf5", borderless: !filled }}
      style={[
        S.iconButton,
        filled && { backgroundColor: C.pale, borderRadius: 12 },
        disabled && { opacity: 0.25 },
      ]}
    >
      <Icon name={name} />
    </Pressable>
  );
}
export function Txt({
  children,
  muted = false,
  size = 15,
  bold = false,
  style,
  lines,
}: {
  children?: ReactNode;
  muted?: boolean;
  size?: number;
  bold?: boolean;
  style?: any;
  lines?: number;
}) {
  return (
    <Text
      numberOfLines={lines}
      style={[
        {
          fontSize: size,
          color: muted ? C.muted : C.ink,
          fontWeight: bold ? "700" : "400",
          lineHeight: Math.round(size * 1.5),
        },
        style,
      ]}
    >
      {children}
    </Text>
  );
}
export function Card({
  children,
  style,
  onPress,
}: {
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
  onPress?(): void;
}) {
  return onPress ? (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: "#eff6fa" }}
      style={[S.card, style]}
    >
      {children}
    </Pressable>
  ) : (
    <View style={[S.card, style]}>{children}</View>
  );
}
export function Badge({
  label,
  active = true,
}: {
  label: string;
  active?: boolean;
}) {
  return (
    <View
      style={{
        backgroundColor: active ? C.pale : C.bg,
        paddingHorizontal: 9,
        paddingVertical: 3,
        borderRadius: 6,
        alignSelf: "flex-start",
      }}
    >
      <Txt size={11} bold style={{ color: active ? C.blue : C.muted }}>
        {label}
      </Txt>
    </View>
  );
}
export function Button({
  title,
  onPress,
  quiet = false,
  danger = false,
  busy = false,
  disabled = false,
}: {
  title: string;
  onPress(): void;
  quiet?: boolean;
  danger?: boolean;
  busy?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={busy || disabled}
      accessibilityRole="button"
      style={[
        S.button,
        {
          backgroundColor: quiet
            ? danger
              ? "#fff1f3"
              : C.pale
            : danger
              ? C.red
              : C.blue,
          opacity: disabled ? 0.4 : 1,
        },
      ]}
    >
      {busy ? (
        <ActivityIndicator color={quiet ? C.blue : "white"} />
      ) : (
        <Txt
          bold
          style={{ color: quiet ? (danger ? C.red : C.blue) : "white" }}
        >
          {title}
        </Txt>
      )}
    </Pressable>
  );
}
export function Input({
  label,
  value,
  onChange,
  placeholder,
  secure = false,
  multiline = false,
  autoCapitalize = "none",
}: {
  label?: string;
  value: string;
  onChange(value: string): void;
  placeholder?: string;
  secure?: boolean;
  multiline?: boolean;
  autoCapitalize?: "none" | "sentences";
}) {
  return (
    <View style={{ gap: 6, marginBottom: 14 }}>
      {label && (
        <Txt size={13} muted>
          {label}
        </Txt>
      )}
      <TextInput
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor="#98a3b3"
        secureTextEntry={secure}
        multiline={multiline}
        autoCapitalize={autoCapitalize}
        autoCorrect={false}
        style={[
          S.input,
          multiline && { minHeight: 120, textAlignVertical: "top" },
        ]}
      />
    </View>
  );
}
export function SearchBar({
  value,
  onChange,
  placeholder,
  onFilter,
}: {
  value: string;
  onChange(v: string): void;
  placeholder: string;
  onFilter?(): void;
}) {
  return (
    <View style={S.search}>
      <Icon name="search" color={C.muted} size={24} />
      <TextInput
        style={{
          flex: 1,
          fontSize: 15,
          color: C.ink,
          paddingVertical: 10,
          paddingHorizontal: 8,
        }}
        placeholder={placeholder}
        placeholderTextColor="#98a3b3"
        value={value}
        onChangeText={onChange}
        returnKeyType="search"
        autoCapitalize="none"
        autoCorrect={false}
      />
      {value.length > 0 && (
        <IconButton
          name="close"
          label="清除搜索"
          onPress={() => onChange("")}
        />
      )}{" "}
      {onFilter && <IconButton name="filter" label="筛选" onPress={onFilter} />}
    </View>
  );
}
export function Section({
  title,
  detail,
  action,
}: {
  title: string;
  detail?: string;
  action?: ReactNode;
}) {
  return (
    <View style={[S.row, { marginBottom: 12 }]}>
      <View style={{ flex: 1 }}>
        <Txt size={18} bold>
          {title}
        </Txt>
        {detail && (
          <Txt size={12} muted>
            {detail}
          </Txt>
        )}
      </View>
      {action}
    </View>
  );
}
export function Empty({
  title = "暂无内容",
  loading = false,
}: {
  title?: string;
  loading?: boolean;
}) {
  return (
    <View style={{ alignItems: "center", paddingVertical: 72, gap: 12 }}>
      {loading ? (
        <ActivityIndicator color={C.blue} />
      ) : (
        <Icon name="folder" color="#a8bbcb" size={52} />
      )}
      <Txt muted>{loading ? "正在加载…" : title}</Txt>
    </View>
  );
}
export function Page({
  children,
  refreshing = false,
  onRefresh,
  scrollKey,
  loading = false,
}: {
  children?: ReactNode;
  refreshing?: boolean;
  onRefresh?(): void;
  scrollKey?: string;
  loading?: boolean;
}) {
  const ref = useRef<React.ElementRef<typeof ScrollView>>(null),
    restored = useRef(false),
    y = useRef(scrollKey ? memory.get("scroll:" + scrollKey, 0) : 0);
  return (
    <ScrollView
      ref={ref}
      contentContainerStyle={S.page}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        onRefresh ? (
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={[C.blue]}
            tintColor={C.blue}
          />
        ) : undefined
      }
      onContentSizeChange={() => {
        if (!loading && !restored.current) {
          restored.current = true;
          ref.current?.scrollTo({ y: y.current, animated: false });
        }
      }}
      onScroll={(e) => {
        if (restored.current && scrollKey)
          memory.set("scroll:" + scrollKey, e.nativeEvent.contentOffset.y);
      }}
      scrollEventThrottle={150}
    >
      {children}
    </ScrollView>
  );
}
export function Sheet({
  title,
  visible,
  onClose,
  children,
}: {
  title: string;
  visible: boolean;
  onClose(): void;
  children?: ReactNode;
}) {
  const inset = useSafeAreaInsets();
  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{
          flex: 1,
          justifyContent: "flex-end",
          backgroundColor: "#17203366",
        }}
      >
        <Pressable
          accessibilityLabel="关闭"
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
        <View
          style={[
            S.sheet,
            { maxHeight: "90%", paddingBottom: Math.max(inset.bottom, 16) },
          ]}
        >
          <View
            style={{
              width: 34,
              height: 4,
              borderRadius: 2,
              backgroundColor: C.line,
              alignSelf: "center",
              marginTop: 10,
            }}
          />
          <View
            style={[
              S.row,
              { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 6 },
            ]}
          >
            <Txt size={20} bold style={{ flex: 1 }}>
              {title}
            </Txt>
            <IconButton name="close" label="关闭" onPress={onClose} />
          </View>
          <ScrollView
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 12 }}
          >
            {children}
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
export type MenuItem = {
  label: string;
  icon?: string;
  danger?: boolean;
  action(): void;
};
export function Menu({
  title,
  items,
  onClose,
}: {
  title: string;
  items: MenuItem[];
  onClose(): void;
}) {
  return (
    <Sheet title={title} visible onClose={onClose}>
      {items.map((item, i) => (
        <Pressable
          key={i}
          accessibilityRole="button"
          onPress={() => {
            onClose();
            item.action();
          }}
          style={[S.row, { paddingVertical: 15, gap: 12 }]}
        >
          <Icon
            name={item.icon || "next"}
            color={item.danger ? C.red : C.blue}
          />
          <Txt style={{ color: item.danger ? C.red : C.ink, flex: 1 }}>
            {item.label}
          </Txt>
          <Icon name="next" color={C.muted} size={20} />
        </Pressable>
      ))}
    </Sheet>
  );
}
export function Options<T extends string | number>({
  label,
  values,
  value,
  onChange,
}: {
  label: string;
  values: { label: string; value: T }[];
  value: T;
  onChange(v: T): void;
}) {
  return (
    <View style={{ marginBottom: 20, gap: 9 }}>
      <Txt size={13} muted>
        {label}
      </Txt>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
        {values.map((option) => (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityState={{ checked: value === option.value }}
            onPress={() => onChange(option.value)}
            style={{
              paddingVertical: 9,
              paddingHorizontal: 13,
              borderRadius: 10,
              backgroundColor: value === option.value ? C.pale : C.bg,
              borderWidth: 1,
              borderColor: value === option.value ? "#b4ddec" : C.bg,
            }}
          >
            <Txt
              size={14}
              style={{ color: value === option.value ? C.blue : C.muted }}
              bold={value === option.value}
            >
              {option.label}
            </Txt>
          </Pressable>
        ))}
      </View>
    </View>
  );
}
export function SettingRow({
  icon,
  title,
  value,
  onPress,
  danger = false,
}: {
  icon: string;
  title: string;
  value?: string;
  onPress?(): void;
  danger?: boolean;
}) {
  return (
    <Pressable
      disabled={!onPress}
      onPress={onPress}
      style={[S.row, { minHeight: 60, gap: 10, paddingVertical: 8 }]}
    >
      <Icon name={icon} color={danger ? C.red : C.blue} />
      <Txt style={{ flex: 1, color: danger ? C.red : C.ink }}>{title}</Txt>
      {value && (
        <Txt muted size={13} lines={1} style={{ maxWidth: "45%" }}>
          {value}
        </Txt>
      )}
      {onPress && <Icon name="next" size={20} color={C.muted} />}
    </Pressable>
  );
}
export function Divider() {
  return <View style={{ height: 1, backgroundColor: "#edf1f5" }} />;
}
export function Pager({
  page,
  total,
  size,
  onChange,
}: {
  page: number;
  total: number;
  size: number;
  onChange(page: number): void;
}) {
  return (
    <View style={[S.row, { marginTop: 4, marginBottom: 14 }]}>
      <IconButton
        name="previous"
        label="上一页"
        disabled={page <= 1}
        onPress={() => onChange(page - 1)}
      />
      <Txt muted size={13} style={{ flex: 1, textAlign: "center" }}>
        {page} / {Math.max(1, Math.ceil(total / size))}　·　{total} 条
      </Txt>
      <IconButton
        name="next"
        label="下一页"
        disabled={page * size >= total}
        onPress={() => onChange(page + 1)}
      />
    </View>
  );
}

export function Media({
  path,
  style,
  aspect = false,
  refreshKey = 0,
  avatar = false,
  onRatio,
  contain = false,
}: {
  path?: string;
  style?: any;
  aspect?: boolean;
  refreshKey?: number;
  avatar?: boolean;
  onRatio?(ratio: number): void;
  contain?: boolean;
}) {
  const { scope } = useApp(),
    [image, setImage] = useState<Row | null>(null),
    [failed, setFailed] = useState(false);
  const [ratio, setRatio] = useState(() => memory.get("ratio:" + path, 1));
  const sourceKey = useRef("");
  useEffect(() => {
    let alive = true;
    if (sourceKey.current !== scope + path) {
      setImage(null);
      setRatio(memory.get("ratio:" + path, 1));
      sourceKey.current = scope + path;
    }
    setFailed(false);
    if (!path) return;
    loadMedia(scope, path, refreshKey > 0)
      .then((result) => {
        if (alive) {
          setImage(result);
          const r = result.width / result.height;
          setRatio(r);
          memory.set("ratio:" + path, r);
          onRatio?.(r);
        }
      })
      .catch(() => {
        if (alive) setFailed(true);
      });
    return () => {
      alive = false;
    };
  }, [path, scope, refreshKey]);
  return (
    <View
      style={[
        { backgroundColor: C.pale, overflow: "hidden" },
        aspect && { aspectRatio: ratio },
        style,
      ]}
    >
      {image ? (
        <Image
          source={{ uri: image.uri }}
          resizeMode={aspect || contain ? "contain" : "cover"}
          style={StyleSheet.absoluteFill}
        />
      ) : (
        <View
          style={[
            StyleSheet.absoluteFill,
            { alignItems: "center", justifyContent: "center" },
          ]}
        >
          {avatar ? (
            <Icon name="artist" size={28} />
          ) : failed ? (
            <Icon name="gallery" color="#a8bbcb" size={36} />
          ) : (
            <ActivityIndicator size="small" color="#98c9dd" />
          )}
        </View>
      )}
    </View>
  );
}
export function confirm(title: string, action: () => void, message?: string) {
  Alert.alert(title, message, [
    { text: "取消", style: "cancel" },
    { text: "确定", onPress: action },
  ]);
}
export const S = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center" },
  page: { padding: 16, paddingBottom: 24, gap: 0 },
  card: {
    backgroundColor: "white",
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
    backgroundColor: "white",
    borderRadius: 10,
    paddingHorizontal: 13,
    paddingVertical: 10,
  },
  search: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "white",
    borderWidth: 1,
    borderColor: C.line,
    borderRadius: 13,
    paddingLeft: 12,
    paddingRight: 4,
    minHeight: 50,
    marginBottom: 16,
  },
  sheet: {
    backgroundColor: "white",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
  },
});
