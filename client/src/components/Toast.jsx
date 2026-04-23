const TYPE_STYLES = {
  success: 'bg-tac-green/10 border-tac-green/40 text-tac-lime',
  error:   'bg-tac-red/10   border-tac-red/40   text-red-400',
  warning: 'bg-tac-amber/10 border-tac-amber/40 text-amber-300',
};

const TYPE_ICON = {
  success: '[OK]',
  error:   '[ERR]',
  warning: '[WARN]',
};

function Toast({ message, type = 'success', onDismiss }) {
  return (
    <div className={`flex items-center gap-3 px-4 py-3 rounded-tac border shadow-xl text-sm font-mono animate-fade-in ${TYPE_STYLES[type]}`}>
      <span className="font-bold text-xs tracking-wider">{TYPE_ICON[type]}</span>
      <span className="flex-1">{message}</span>
      <button
        onClick={onDismiss}
        className="min-h-[32px] min-w-[32px] flex items-center justify-center opacity-70 hover:opacity-100 transition text-tac-muted hover:text-tac-text"
        aria-label="Dismiss"
      >
        ✕
      </button>
    </div>
  );
}

export function ToastContainer({ toasts, onDismiss }) {
  if (!toasts.length) return null;
  return (
    <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-2 w-full max-w-sm pointer-events-none">
      {toasts.map((t) => (
        <div key={t.id} className="pointer-events-auto">
          <Toast message={t.message} type={t.type} onDismiss={() => onDismiss(t.id)} />
        </div>
      ))}
    </div>
  );
}

export default Toast;
