import { describe, expect, it } from "vitest";
import type { ModelOptionItem } from "../../types";
import { filterModelsForCapability, hasCapability } from "./modelCapabilities";

describe("model capability helpers", () => {
  it("只讓 Vision 模型進入 OCR 選項", () => {
    const options = [
      { model: "vision", providerId: 1, capabilities: ["VISION"], capabilitySource: "AUTO" },
      { model: "text", providerId: 1, capabilities: [], capabilitySource: "UNKNOWN" },
    ] satisfies ModelOptionItem[];

    expect(filterModelsForCapability(options, "VISION").map((option) => option.model)).toEqual(["vision"]);
  });

  it("只讓語音轉錄模型進入 Transcription 選項", () => {
    const options = [
      { model: "audio", providerId: 1, capabilities: ["AUDIO_TRANSCRIPTION"], capabilitySource: "MANUAL" },
      { model: "vision", providerId: 1, capabilities: ["VISION"], capabilitySource: "AUTO" },
    ] satisfies ModelOptionItem[];

    expect(filterModelsForCapability(options, "AUDIO_TRANSCRIPTION").map((option) => option.model)).toEqual(["audio"]);
    expect(hasCapability(options[1], "AUDIO_TRANSCRIPTION")).toBe(false);
  });
});
