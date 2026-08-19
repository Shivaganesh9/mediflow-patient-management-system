export function Spinner() {
  return <span className="spinner" aria-label="Loading" />;
}

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-state">
      <Spinner />
      <p>{label}</p>
    </div>
  );
}

export function FullPageLoader() {
  return (
    <div className="full-page-loader">
      <div className="brand brand-loader">
        <h1>MediFlow</h1>
        <p>Patient management platform</p>
      </div>
      <LoadingState label="Signing you in…" />
    </div>
  );
}

export function ErrorState({ message }: { message: string }) {
  return <div className="state-box state-error">{message}</div>;
}

export function EmptyState({ message }: { message: string }) {
  return <div className="state-box state-empty">{message}</div>;
}

export function AccessDenied() {
  return (
    <div className="page-stack">
      <section className="card">
        <div className="state-box state-error">
          <h3>Access denied</h3>
          <p>Your role does not permit access to this page.</p>
        </div>
      </section>
    </div>
  );
}

export function NotFound() {
  return (
    <div className="page-stack">
      <section className="card">
        <div className="state-box state-empty">
          <h3>Page not found</h3>
          <p>The page you requested does not exist.</p>
        </div>
      </section>
    </div>
  );
}