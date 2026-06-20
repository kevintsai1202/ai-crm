/**
 * 版面抽屜：右側滑入，列出目前隱藏的儀表板區塊，可逐一加回，並提供還原預設順序。
 * 函式級註解：加回與還原都委派給父層（DashboardPage）統一走存檔邏輯，本元件只負責呈現與觸發。
 */
interface HiddenBlock {
  /** 區塊 id */
  id: string;
  /** 區塊顯示名 */
  title: string;
}

export function LayoutDrawer({ hiddenBlocks, onAdd, onReset, onClose }: {
  hiddenBlocks: HiddenBlock[];
  onAdd: (id: string) => void;
  onReset: () => void;
  onClose: () => void;
}) {
  return (
    <div className="drawer-overlay" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <strong>版面設定</strong>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="drawer-body">
          <p className="drawer-hint">隱藏的區塊（點＋加回儀表板）</p>
          {hiddenBlocks.length === 0 ? (
            <div className="sr-empty">目前所有區塊都已顯示</div>
          ) : (
            hiddenBlocks.map((b) => (
              <div className="drawer-item" key={b.id}>
                <span>{b.title}</span>
                <button type="button" className="btn-secondary" onClick={() => onAdd(b.id)}>＋ 加回</button>
              </div>
            ))
          )}
        </div>
        <div className="drawer-footer">
          <button type="button" className="btn-reset-layout" onClick={onReset}>↺ 還原預設版面</button>
        </div>
      </aside>
    </div>
  );
}
