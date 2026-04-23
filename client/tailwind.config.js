/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Tactical green-on-dark palette
        tac: {
          black:    '#0a0c0f',   // deepest background
          darker:   '#0f1318',   // page background
          dark:     '#151a21',   // card / panel background
          mid:      '#1c2530',   // elevated surfaces, inputs
          border:   '#2a3545',   // subtle borders
          muted:    '#4a5a6a',   // disabled / muted text
          text:     '#c8d6df',   // primary body text
          bright:   '#e2eaf0',   // headings / emphasis
          green:    '#22c55e',   // primary accent (status OK, nav active)
          lime:     '#4ade80',   // hover / highlight
          amber:    '#f59e0b',   // warnings, pending
          red:      '#ef4444',   // errors, urgent, overdue
          cyan:     '#06b6d4',   // informational, links
          blue:     '#3b82f6',   // secondary accent (FOH)
        },
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"Fira Code"', '"SF Mono"', 'Consolas', 'monospace'],
        sans: ['"Inter"', 'system-ui', '-apple-system', 'sans-serif'],
      },
      fontSize: {
        // Touch-target readable sizes for POS terminals
        'touch-sm': ['1.125rem', { lineHeight: '1.75rem' }],
        'touch-md': ['1.375rem', { lineHeight: '2rem'   }],
        'touch-lg': ['1.75rem',  { lineHeight: '2.5rem' }],
      },
      minHeight: {
        touch: '3rem', // 48px minimum touch target
      },
      borderRadius: {
        'tac': '2px', // angular / military feel
      },
      boxShadow: {
        'tac':      '0 0 0 1px rgba(34,197,94,0.15)',
        'tac-glow': '0 0 8px rgba(34,197,94,0.2), 0 0 0 1px rgba(34,197,94,0.3)',
      },
    },
  },
  plugins: [],
};
