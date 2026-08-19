import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { authApi } from '../api/auth';
import { clearSession, getAccessToken, setTokens } from '../api/client';
import { UserProfile } from '../types/auth';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  status: AuthStatus;
  user: UserProfile | null;
  roles: string[];
  hasRole: (...required: string[]) => boolean;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    let cancelled = false;

    const onUnauthorized = () => {
      if (cancelled) return;
      clearSession();
      setUser(null);
      setStatus('unauthenticated');
    };

    const token = getAccessToken();
    if (!token) {
      setStatus('unauthenticated');
    } else {
      authApi
        .me()
        .then((profile) => {
          if (!cancelled) {
            setUser(profile);
            setStatus('authenticated');
          }
        })
        .catch(() => {
          if (!cancelled) {
            clearSession();
            setStatus('unauthenticated');
          }
        });
    }

    window.addEventListener('mediflow:unauthorized', onUnauthorized);
    return () => {
      cancelled = true;
      window.removeEventListener('mediflow:unauthorized', onUnauthorized);
    };
  }, []);

  const login = useCallback(async (usernameOrEmail: string, password: string) => {
    const response = await authApi.login({ usernameOrEmail, password });
    setTokens(response.accessToken, response.refreshToken);
    const profile = await authApi.me();
    setUser(profile);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore network/status errors; the local session is cleared regardless
    }
    clearSession();
    setUser(null);
    setStatus('unauthenticated');
  }, []);

  const roles = useMemo(() => user?.roles ?? [], [user]);

  const hasRole = useCallback((...required: string[]) => roles.some((r) => required.includes(r)), [roles]);

  const value = useMemo(
    () => ({ status, user, roles, hasRole, login, logout }),
    [status, user, roles, hasRole, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}