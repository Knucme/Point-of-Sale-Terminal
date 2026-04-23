export default function ConfirmModal({
  title,
  message,
  confirmLabel = 'CONFIRM',
  cancelLabel  = 'CANCEL',
  onConfirm,
  onCancel,
  danger = false,
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="tac-panel shadow-tac-glow max-w-sm w-full p-6 animate-fade-in">
        <div className="flex items-center gap-2 mb-2">
          <span className="font-mono text-xs text-tac-green tracking-widest">{danger ? '[!]' : '[?]'}</span>
          <h2 className="text-lg font-bold text-tac-bright font-mono uppercase tracking-wide">{title}</h2>
        </div>
        {message && <p className="text-tac-text text-sm mb-6 ml-6">{message}</p>}
        <div className="flex gap-3 justify-end">
          <button
            onClick={onCancel}
            className="tac-btn-muted min-h-[48px] px-5"
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            className={`min-h-[48px] px-5 ${danger ? 'tac-btn-danger' : 'tac-btn'}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
