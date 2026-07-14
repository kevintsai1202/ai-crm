import type { ModelCapability, ModelOptionItem } from "../../types";

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
