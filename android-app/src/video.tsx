import { requireNativeComponent, ViewProps } from "react-native";

// Register each view once. Inline previews use a texture surface; full screen uses a surface view.
type VideoProps = ViewProps & {
  path: string;
  rate: number;
  muted?: boolean;
};
export const NativeVideo = requireNativeComponent<VideoProps>("DazoVideo");
export const NativeInlineVideo =
  requireNativeComponent<VideoProps>("DazoInlineVideo");
