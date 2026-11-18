export type Tab = "home" | "artists" | "free" | "more";
export type Screen = {
  name: Tab | "gallery" | "history" | "tasks" | "users" | "account" | "storage";
  path?: string;
  artistId?: string;
};
export type Navigation = { tab: Tab; stacks: Partial<Record<Tab, Screen[]>> };
export const tabNames: Tab[] = ["home", "artists", "free", "more"];
export function normalizeNavigation(value: any): Navigation {
  const tab: Tab = tabNames.includes(value?.tab) ? value.tab : "home";
  return {
    tab,
    stacks: value?.stacks || {
      [tab]: Array.isArray(value?.stack) ? value.stack : [],
    },
  };
}
export function selectTab(value: Navigation, tab: Tab): Navigation {
  return { tab, stacks: { ...value.stacks, [tab]: value.stacks[tab] || [] } };
}
export function pushScreen(value: Navigation, screen: Screen): Navigation {
  return {
    ...value,
    stacks: {
      ...value.stacks,
      [value.tab]: [...(value.stacks[value.tab] || []), screen],
    },
  };
}
export function popScreen(value: Navigation): Navigation {
  const stack = value.stacks[value.tab] || [];
  return stack.length
    ? { ...value, stacks: { ...value.stacks, [value.tab]: stack.slice(0, -1) } }
    : selectTab(value, "home");
}
