import React from "react";
import { Options, Sheet } from "./ui";
export const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];
export function PlaybackSpeedSheet({
  visible,
  rate,
  setRate,
  close,
}: {
  visible: boolean;
  rate: number;
  setRate(value: number): void;
  close(): void;
}) {
  return (
    <Sheet visible={visible} title="播放速度" onClose={close}>
      <Options
        label="默认倍速"
        values={playbackRates.map((value) => ({ value, label: value + "×" }))}
        value={rate}
        onChange={(value) => {
          setRate(value);
          close();
        }}
      />
    </Sheet>
  );
}
