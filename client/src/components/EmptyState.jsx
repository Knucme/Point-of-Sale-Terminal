export default function EmptyState({ message = 'NO DATA AVAILABLE.' }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-tac-muted font-mono">
      <div className="text-2xl mb-3 opacity-40">[ ]</div>
      <p className="text-xs text-center px-4 uppercase tracking-widest">{message}</p>
    </div>
  );
}
