import { describe, expect, it } from "vitest";
import type { ModelOptionItem } from "../../types";
import {
  buildModelAssignments,
  canEditCapabilities,
  filterModelsForCapability,
  hasCapability,
  isSameModelPair,
  modelOptionKey,
} from "./modelCapabilities";

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

  it("同名模型以 provider pair 區分並將正確 Provider 儲存至 OCR assignment", () => {
    const options = [
      { model: "shared", providerId: 1, capabilities: [], capabilitySource: "UNKNOWN" },
      { model: "shared", providerId: 2, capabilities: ["VISION"], capabilitySource: "MANUAL" },
    ] satisfies ModelOptionItem[];
    const ocrOptions = filterModelsForCapability(options, "VISION");

    expect(modelOptionKey(options[0])).not.toBe(modelOptionKey(options[1]));
    expect(ocrOptions.map(modelOptionKey)).toEqual([modelOptionKey(options[1])]);
    expect(buildModelAssignments(options, {
      chatModel: "shared",
      chatProviderId: 1,
      ocrKey: modelOptionKey(options[1]),
      transcriptionKey: "",
    })).toEqual({
      chatModel: "shared",
      chatProviderId: 1,
      ocrModel: "shared",
      ocrProviderId: 2,
      transcriptionModel: null,
      transcriptionProviderId: null,
    });
    expect(isSameModelPair(options[0], "shared", 1)).toBe(true);
    expect(isSameModelPair(options[1], "shared", 1)).toBe(false);
  });

  it("AUTO 能力為唯讀，UNKNOWN 與 MANUAL 可由 Admin 管理", () => {
    expect(canEditCapabilities({ model: "auto", providerId: 1, capabilities: ["VISION"], capabilitySource: "AUTO" })).toBe(false);
    expect(canEditCapabilities({ model: "unknown", providerId: 1, capabilities: [], capabilitySource: "UNKNOWN" })).toBe(true);
    expect(canEditCapabilities({ model: "manual", providerId: 1, capabilities: [], capabilitySource: "MANUAL" })).toBe(true);
  });
});
