import { ReactNode } from 'react';
import { useAuth } from '../context/AuthContext';
import { AccessDenied } from '../components/PageState';

export function RoleRoute({ roles, children }: { roles: string[]; children: ReactNode }) {
  const { hasRole } = useAuth();
  if (!hasRole(...roles)) return <AccessDenied />;
  return <>{children}</>;
}