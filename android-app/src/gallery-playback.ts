type Item = { type?: string };
type Layout = { offset: number; length: number };

/** Hold a new preview until dragging/flinging has finished, including Android's delayed momentum events. */
export function createScrollActivity(onIdle: (idle: boolean) => void) {
  let dragging = false,
    timer: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => {
    if (timer) clearTimeout(timer);
    timer = undefined;
  };
  const idle = () => {
    cancel();
    if (!dragging) onIdle(true);
  };
  const scroll = () => {
    cancel();
    onIdle(false);
    if (!dragging) timer = setTimeout(idle, 160);
  };
  return {
    beginDrag() {
      dragging = true;
      scroll();
    },
    endDrag() {
      dragging = false;
      scroll();
    },
    scroll,
    endMomentum: idle,
    reset() {
      dragging = false;
      idle();
    },
    dispose: cancel,
  };
}

/** Only the video covering the viewport centre may own the inline player. */
export function centeredVideoIndex(
  items: Item[],
  layouts: Layout[],
  offset: number,
  viewport: number,
) {
  if (viewport <= 0 || !layouts.length) return null;
  const centre = offset + viewport / 2;
  let low = 0,
    high = layouts.length;
  while (low < high) {
    const mid = (low + high) >>> 1;
    if (layouts[mid].offset + layouts[mid].length <= centre) low = mid + 1;
    else high = mid;
  }
  const layout = layouts[low];
  if (!layout || items[low]?.type !== "video" || centre < layout.offset)
    return null;
  const visible =
    Math.min(offset + viewport, layout.offset + layout.length) -
    Math.max(offset, layout.offset);
  return visible >= Math.min(viewport, layout.length) * 0.6 ? low : null;
}

export type ImageRect = { x: number; y: number; width: number; height: number };
export type ImageTransition = { name: string; path: string; rect: ImageRect };
export function fitImageRect(
  ratio: number,
  width: number,
  height: number,
  top = 0,
): ImageRect {
  const validRatio = Number.isFinite(ratio) && ratio > 0 ? ratio : 1;
  const imageWidth = Math.min(width, height * validRatio);
  const imageHeight = imageWidth / validRatio;
  return {
    x: (width - imageWidth) / 2,
    y: top + (height - imageHeight) / 2,
    width: imageWidth,
    height: imageHeight,
  };
}
