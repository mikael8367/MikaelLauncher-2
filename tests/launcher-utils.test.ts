import { describe, expect, it } from "vitest";

import { MEMORY_PRESETS, getMemoryStatus } from "../lib/launcher-utils";

describe("launcher memory presets", () => {
  it("keeps the documented RAM presets in ascending order", () => {
    expect(MEMORY_PRESETS).toEqual([512, 768, 1024, 1536, 2048, 4096]);
    expect([...MEMORY_PRESETS].sort((a, b) => a - b)).toEqual([...MEMORY_PRESETS]);
  });

  it("classifies the recommended Survival memory as balanced", () => {
    expect(getMemoryStatus(1024)).toMatchObject({ label: "Balanceada", tone: "success" });
  });

  it("warns when the configured memory is high", () => {
    expect(getMemoryStatus(4096)).toMatchObject({ label: "Alta", tone: "warning" });
    expect(getMemoryStatus(4096).detail).toContain("2 GB");
  });

  it("identifies low-memory presets for lightweight profiles", () => {
    expect(getMemoryStatus(512).label).toBe("Muito baixa");
    expect(getMemoryStatus(768).label).toBe("Baixa");
  });
});
