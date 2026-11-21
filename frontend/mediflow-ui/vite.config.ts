import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  // Demo mode talks to an in-browser synthetic API. Shipping it as a production build would hide
  // the real backend behind fake data, so refuse instead of relying on reviewer memory.
  if (mode === 'production' && env.VITE_DEMO_MODE === 'true') {
    throw new Error(
      'Refusing to build production with VITE_DEMO_MODE=true. Demo mode is local-only: use `npm run demo` (vite --mode demo).',
    );
  }

  return {
    plugins: [react()],
    server: { port: 3000 },
  };
});
