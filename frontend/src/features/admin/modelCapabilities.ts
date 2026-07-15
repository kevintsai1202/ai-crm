import type { ModelCapability, ModelOptionItem } from "../../types";

/** AI 用途 assignment API 的完整 model-provider pair payload。 */
export interface ModelAssignmentsPayload {
  chatModel: string;
  chatProviderId: number | null;
  ocrModel: string | null;
  ocrProviderId: number | null;
  transcriptionModel: string | null;
  transcriptionProviderId: number | null;
}

/** 以 JSON tuple 建立無碰撞且可序列化的 model-provider select key。 */
export function modelPairKey(model: string | null, providerId: number | null): string {
  return model ? JSON.stringify([model, providerId]) : "";
}

/** 取得模型選項的穩定 pair key。 */
export function modelOptionKey(option: ModelOptionItem): string {
  return modelPairKey(option.model, option.providerId);
}

/** 比對指定模型是否為完全相同的 model-provider pair。 */
export function isSameModelPair(option: ModelOptionItem, model: string, providerId: number | null): boolean {
  return option.model === model && option.providerId === providerId;
}

/** AUTO metadata 為唯讀；只有具 Provider 的 UNKNOWN／MANUAL 可人工管理。 */
export function canEditCapabilities(option: ModelOptionItem): boolean {
  return option.providerId != null && option.capabilitySource !== "AUTO";
}

/** 判斷模型是否具有指定且已由後端確認的能力。 */
export function hasCapability(option: ModelOptionItem, capability: ModelCapability): boolean {
  return option.capabilities.includes(capability);
}

/** 依用途能力篩選模型，避免不相容模型出現在 assignment 下拉。 */
export function filterModelsForCapability(
  options: ModelOptionItem[],
  capability: ModelCapability,
): ModelOptionItem[] {
  return options.filter((option) => hasCapability(option, capability));
}

/** 以 select pair key 解析用途模型，並保留既有 Chat model-provider pair。 */
export function buildModelAssignments(
  options: ModelOptionItem[],
  selection: {
    chatModel: string;
    chatProviderId: number | null;
    ocrKey: string;
    transcriptionKey: string;
  },
): ModelAssignmentsPayload {
  const selectedOcr = options.find((option) => modelOptionKey(option) === selection.ocrKey) ?? null;
  const selectedTranscription = options.find((option) => modelOptionKey(option) === selection.transcriptionKey) ?? null;
  return {
    chatModel: selection.chatModel,
    chatProviderId: selection.chatProviderId,
    ocrModel: selectedOcr?.model ?? null,
    ocrProviderId: selectedOcr?.providerId ?? null,
    transcriptionModel: selectedTranscription?.model ?? null,
    transcriptionProviderId: selectedTranscription?.providerId ?? null,
  };
}
