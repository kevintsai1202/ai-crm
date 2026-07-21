/**
 * AI 等待首字動態指示器：三顆跳動圓點 + 文字標籤。
 * 函式級註解：用於所有 AI 回答的「等待首字」狀態，比純文字 blink 更明顯。
 *
 * @param label 顯示文字（預設「AI 正在思考」）
 */
export function AiThinkingIndicator({ label }: { label?: string }) {
  const { t } = useTranslation("common");
  return (
    <div className="ai-thinking">
      <div className="ai-thinking__dots">
        <span className="ai-thinking__dot" />
        <span className="ai-thinking__dot" />
        <span className="ai-thinking__dot" />
      </div>
      <span className="ai-thinking__label">{label ?? t("ai.thinking")}</span>
    </div>
  );
}
import { useTranslation } from "react-i18next";
