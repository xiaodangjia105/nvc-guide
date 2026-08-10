import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { UserPlus, Loader2, MessageSquare } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routes';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');

    if (!username.trim() || !email.trim() || !password || !confirmPassword) {
      setError('请填写所有字段');
      return;
    }

    if (!email.includes('@')) {
      setError('请输入有效的邮箱地址');
      return;
    }

    if (password.length < 6) {
      setError('密码长度不能少于 6 位');
      return;
    }

    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      await register({ username: username.trim(), email: email.trim(), password });
      navigate(ROUTES.nvcHub, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 px-4">
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="w-full max-w-md"
      >
        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-14 h-14 bg-gradient-to-br from-primary-500 to-primary-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30 mb-4">
            <MessageSquare className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">
            注册 NVC 练习助手
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            创建账号，开始非暴力沟通练习
          </p>
        </div>

        {/* 表单卡片 */}
        <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-xl border border-slate-200 dark:border-slate-700 p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* 错误提示 */}
            {error && (
              <div className="bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 text-sm rounded-lg px-4 py-3">
                {error}
              </div>
            )}

            {/* 用户名 */}
            <div>
              <label
                htmlFor="username"
                className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5"
              >
                用户名
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                autoComplete="username"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
              />
            </div>

            {/* 邮箱 */}
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5"
              >
                邮箱
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="请输入邮箱地址"
                autoComplete="email"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
              />
            </div>

            {/* 密码 */}
            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5"
              >
                密码
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码（至少 6 位）"
                autoComplete="new-password"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
              />
            </div>

            {/* 确认密码 */}
            <div>
              <label
                htmlFor="confirmPassword"
                className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5"
              >
                确认密码
              </label>
              <input
                id="confirmPassword"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="请再次输入密码"
                autoComplete="new-password"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-700 text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
              />
            </div>

            {/* 注册按钮 */}
            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-primary-500 to-primary-600 text-white font-medium shadow-lg shadow-primary-500/30 hover:shadow-primary-500/40 disabled:opacity-60 transition-all"
            >
              {loading ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <>
                  <UserPlus className="w-5 h-5" />
                  注册
                </>
              )}
            </button>
          </form>
        </div>

        {/* 登录链接 */}
        <p className="text-center text-sm text-slate-500 dark:text-slate-400 mt-6">
          已有账号？{' '}
          <Link
            to={ROUTES.login}
            className="text-primary-500 hover:text-primary-600 font-medium"
          >
            立即登录
          </Link>
        </p>
      </motion.div>
    </div>
  );
}
