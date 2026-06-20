/**
 * AI 功能徽章：統一標示「此功能由 AI 驅動」。
 * 函式級註解：onDark 用於深色/漸層底（白底徽章），預設用於淺色底（漸層徽章）。
 */
export function AiBadge({ onDark = false }: { onDark?: boolean }) {
  return (
    <span className={onDark ? "ai-badge on-dark" : "ai-badge"} title="此功能由 AI 驅動">
      <span aria-hidden="true">✨</span> AI
    </span>
  );
}
