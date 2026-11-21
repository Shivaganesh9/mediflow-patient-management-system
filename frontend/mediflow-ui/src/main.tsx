import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import './styles.css';

/**
 * Demo mode is a build-time flag (`vite --mode demo`). The synthetic API is imported lazily so the
 * production bundle never loads it; rendering waits until it is installed, otherwise the first
 * authenticated request could escape to the real network.
 */
const demoBuild = import.meta.env.VITE_DEMO_MODE === 'true';

function renderApp() {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </React.StrictMode>
  );
}

if (demoBuild) {
  void import('./demo/install')
    .then(({ installDemoApi }) => installDemoApi())
    .finally(renderApp);
} else {
  renderApp();
}
