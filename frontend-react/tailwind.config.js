/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        studio: {
          bg: '#0A0A0C',          // Ultra-dark background
          panel: '#151518',       // Matte dark charcoal panel
          surface: '#1A1A1E',     // Secondary surface
          elevated: '#212126',    // Hover / elevated popup
          border: '#28282E',      // Fine crisp border
          borderLight: '#363640', // Highlight border
          gold: '#E5A93C',        // Golden amber accent from image
          goldBg: '#F59E0B',
          blue: '#3B82F6',        // Electric blue sliders / accents
          blueDark: '#1D4ED8',
          green: '#22C55E',       // Neon green toggle switch
          red: '#EF4444',
          textMuted: '#71717A',
          textSub: '#A1A1AA',
          textMain: '#F4F4F5'
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Courier New', 'monospace']
      }
    },
  },
  plugins: [],
}
