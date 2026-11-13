import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { authApi } from '../api/auth';
import {
  clearSession,
  hasSession,
  SESSION_ENDED_EVENT,
  SessionEndReason,
  setTokens,
} from '../api/client';
import { UserProfile } from '../types/auth';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  status: AuthStatus;
  user: UserProfile | null;
  roles: string[];
  /** One-shot message explaining why the session ended (expiry, password change, sign-out). */
  sessionNotice: string | null;
  setSessionNotice: (message: string | null) => void;
  hasRole: (...required: string[]) => boolean;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const SESSION_ENDED_MESSAGES: Record<string, string> = {
  expired: 'Your session has expired. Please sign in again.',
  'signed-out': 'You have been signed out.',
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const onSessionEnded = (event: Event) => {
      if (cancelled) return;
      const reason = (event as CustomEvent<{ reason?: SessionEndReason }>).detail?.reason ?? 'expired';
      clearSession();
      setUser(null);
      setStatus('unauthenticated');
      setSessionNotice(SESSION_ENDED_MESSAGES[reason] ?? SESSION_ENDED_MESSAGES.expired);
    };

    // Restoration after a reload: with only a refresh token left (access token already
    // expired and pruned) the API client transparently refreshes and retries /me.
    if (hasSession()) {
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
            setUser(null);
            setStatus('unauthenticated');
          }
        });
    } else {
      setStatus('unauthenticated');
    }

    window.addEventListener(SESSION_ENDED_EVENT, onSessionEnded);
    return () => {
      cancelled = true;
      window.removeEventListener(SESSION_ENDED_EVENT, onSessionEnded);
    };
  }, []);

  const login = useCallback(async (usernameOrEmail: string, password: string) => {
    const response = await authApi.login({ usernameOrEmail, password });
    setTokens(response.accessToken, response.refreshToken, response.expiresIn);
    setSessionNotice(null);
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
    setSessionNotice(SESSION_ENDED_MESSAGES['signed-out']);
  }, []);

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    await authApi.changePassword({ currentPassword, newPassword });
    // The server revoked every session, so the client must drop its tokens as well.
    clearSession();
    setUser(null);
    setStatus('unauthenticated');
    setSessionNotice('Password changed successfully. Please sign in again with your new password.');
  }, []);

  const roles = useMemo(() => user?.roles ?? [], [user]);

  const hasRole = useCallback((...required: string[]) => roles.some((r) => required.includes(r)), [roles]);

  const value = useMemo(
    () => ({
      status,
      user,
      roles,
      sessionNotice,
      setSessionNotice,
      hasRole,
      login,
      logout,
      changePassword,
    }),
    [status, user, roles, sessionNotice, hasRole, login, logout, changePassword],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
