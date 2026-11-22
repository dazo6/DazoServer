export function dragSelection(
  base: ReadonlySet<string>,
  names: readonly string[],
  start: number,
  end: number,
  select: boolean,
  eligible: (name: string) => boolean,
  limit = 1000,
): Set<string> {
  const next = new Set(base);
  for (
    let i = Math.max(0, Math.min(start, end));
    i <= Math.min(names.length - 1, Math.max(start, end));
    i++
  ) {
    const name = names[i];
    if (!eligible(name)) continue;
    if (!select) next.delete(name);
    else if (next.size < limit) next.add(name);
  }
  return next;
}

export function selectionScrollStep(
  y: number,
  top: number,
  bottom: number,
): number {
  if (bottom <= top) return 0;
  const edge = Math.min(64, (bottom - top) / 3);
  if (y < top + edge) return -24 * Math.min(1, (top + edge - y) / edge);
  if (y > bottom - edge) return 24 * Math.min(1, (y - bottom + edge) / edge);
  return 0;
}
