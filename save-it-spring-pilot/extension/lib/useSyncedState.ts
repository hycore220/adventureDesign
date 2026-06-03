import { useEffect, useState } from "react";

export function useSyncedState<T>(key: string, initial: T) {
  const [value, setValue] = useState<T>(initial);

  useEffect(() => {
    browser.storage.local.get(key).then((r) => {
      if (r[key] !== undefined) {
        setValue((prev) => {
          const next = r[key] as T;
          if (JSON.stringify(prev) === JSON.stringify(next)) return prev;
          return next;
        });
      }
    });
  }, [key]);

  useEffect(() => {
    const listener = (
      changes: Record<string, { newValue?: unknown; oldValue?: unknown }>,
      area: string
    ) => {
      if (area !== "local") return;
      if (!changes[key]) return;
      const next = changes[key].newValue;
      if (next === undefined) return;
      setValue((prev) => {
        if (JSON.stringify(prev) === JSON.stringify(next)) return prev;
        return next as T;
      });
    };
    browser.storage.onChanged.addListener(listener);
    return () => browser.storage.onChanged.removeListener(listener);
  }, [key]);

  function set(next: T | ((prev: T) => T)) {
    setValue((prev) => {
      const v =
        typeof next === "function" ? (next as (p: T) => T)(prev) : next;
      browser.storage.local.set({ [key]: v }).catch(() => {});
      return v;
    });
  }

  return [value, set] as const;
}
