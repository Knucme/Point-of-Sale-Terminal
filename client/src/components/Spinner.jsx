const SIZES = { sm: 'h-4 w-4 border-2', md: 'h-8 w-8 border-2', lg: 'h-12 w-12 border-2' };

export default function Spinner({ size = 'md' }) {
  return (
    <div className="flex items-center justify-center">
      <div className={`${SIZES[size]} animate-spin rounded-none border-tac-border border-t-tac-green`} />
    </div>
  );
}
