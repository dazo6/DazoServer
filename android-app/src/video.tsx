import { requireNativeComponent, ViewProps } from "react-native";

// Register once: inline previews and the full-screen viewer share this native component.
export const NativeVideo = requireNativeComponent<
  ViewProps & {
    path: string;
    rate: number;
    inline?: boolean;
    muted?: boolean;
  }
>("DazoVideo");
