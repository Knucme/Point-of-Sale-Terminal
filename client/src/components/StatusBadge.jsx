const LABELS = {
  PENDING:     'PENDING',
  IN_PROGRESS: 'IN PROGRESS',
  DELAYED:     'DELAYED',
  COMPLETED:   'COMPLETED',
};

export default function StatusBadge({ status }) {
  return (
    <span className={`status-badge-${status} inline-flex items-center px-2.5 py-0.5 rounded-tac text-xs font-mono font-semibold tracking-wider uppercase`}>
      {LABELS[status] ?? status}
    </span>
  );
}
