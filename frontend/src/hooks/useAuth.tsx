import { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { ReactNode } from 'react';
import { authApi } from '../api/auth';
import type { User, LoginRequest, RegisterRequest } from '../api/auth';

// ========== Context 类型 ==========

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
}

interface AuthContextValue extends AuthState {
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
}

// ========== Context ==========

const AuthContext = createContext<AuthContextValue | null>(null);

// ========== Provider ==========

const TOKEN_KEY = 'token';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: localStorage.getItem(TOKEN_KEY),
    loading: true,
  });

  // 应用启动时尝试恢复用户信息
  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      setState({ user: null, token: null, loading: false });
      return;
    }
    authApi.getMe()
      .then((user) => setState({ user, token, loading: false }))
      .catch(() => {
        // token 无效，清除
        localStorage.removeItem(TOKEN_KEY);
        setState({ user: null, token: null, loading: false });
      });
  }, []);

  const login = useCallback(async (data: LoginRequest) => {
    const res = await authApi.login(data);
    localStorage.setItem(TOKEN_KEY, res.token);
    setState({ user: res.user, token: res.token, loading: false });
  }, []);

  const register = useCallback(async (data: RegisterRequest) => {
    const res = await authApi.register(data);
    localStorage.setItem(TOKEN_KEY, res.token);
    setState({ user: res.user, token: res.token, loading: false });
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setState({ user: null, token: null, loading: false });
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// ========== Hook ==========

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
