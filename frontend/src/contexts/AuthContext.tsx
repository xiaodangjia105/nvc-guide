import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { authApi, type User } from '../api/auth';

// ========== 类型 ==========

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AuthContextValue extends AuthState {
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

// ========== Context ==========

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'token';

// ========== Provider ==========

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem(TOKEN_KEY)
  );
  const [isLoading, setIsLoading] = useState(true);

  // 启动时用 token 恢复用户信息
  useEffect(() => {
    if (!token) {
      setIsLoading(false);
      return;
    }

    authApi
      .getMe()
      .then((u) => setUser(u))
      .catch(() => {
        // token 失效，清除
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
      })
      .finally(() => setIsLoading(false));
  }, [token]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await authApi.login({ username, password });
    localStorage.setItem(TOKEN_KEY, res.token);
    setToken(res.token);
    setUser(res.user);
  }, []);

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      const res = await authApi.register({ username, email, password });
      localStorage.setItem(TOKEN_KEY, res.token);
      setToken(res.token);
      setUser(res.user);
    },
    []
  );

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setUser(null);
  }, []);

  const value: AuthContextValue = {
    user,
    token,
    isAuthenticated: !!user,
    isLoading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// ========== Hook ==========

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
