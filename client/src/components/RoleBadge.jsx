const COLORS = {
  BOH:     'bg-tac-red/20 text-red-400 border border-tac-red/30',
  FOH:     'bg-tac-cyan/20 text-cyan-400 border border-tac-cyan/30',
  MANAGER: 'bg-tac-green/20 text-tac-lime border border-tac-green/30',
};

export default function RoleBadge({ role }) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-tac text-xs font-mono font-bold uppercase tracking-widest ${COLORS[role] ?? 'bg-tac-mid text-tac-muted border border-tac-border'}`}
    >
      {role}
    </span>
  );
}
