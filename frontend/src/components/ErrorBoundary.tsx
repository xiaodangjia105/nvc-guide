import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** 自定义 fallback 渲染 */
  fallback?: ReactNode;
  /** 错误回调（用于上报） */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  showDetails: boolean;
}

/**
 * 全局错误边界组件
 *
 * 捕获子组件渲染过程中的未处理错误，防止整个应用白屏。
 * 显示友好的错误页面，支持重试和错误详情查看。
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null, showDetails: false };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[ErrorBoundary] Caught error:', error, errorInfo);
    this.props.onError?.(error, errorInfo);
  }

  private handleRetry = (): void => {
    this.setState({ hasError: false, error: null, showDetails: false });
  };

  private toggleDetails = (): void => {
    this.setState((prev) => ({ showDetails: !prev.showDetails }));
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex flex-col items-center justify-center min-h-[400px] p-8 text-center">
          <div className="w-16 h-16 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center mb-6">
            <AlertTriangle className="w-8 h-8 text-red-500" />
          </div>

          <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-2">
            页面出错了
          </h2>

          <p className="text-slate-500 dark:text-slate-400 mb-6 max-w-md">
            抱歉，页面渲染时发生了意外错误。您可以尝试刷新页面或返回首页。
          </p>

          <div className="flex gap-3 mb-4">
            <button
              onClick={this.handleRetry}
              className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
            >
              <RefreshCw className="w-4 h-4" />
              重试
            </button>
            <button
              onClick={() => window.location.href = '/'}
              className="px-4 py-2 bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors"
            >
              返回首页
            </button>
          </div>

          <button
            onClick={this.toggleDetails}
            className="text-sm text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 underline"
          >
            {this.state.showDetails ? '隐藏详情' : '查看错误详情'}
          </button>

          {this.state.showDetails && this.state.error && (
            <div className="mt-4 p-4 bg-slate-100 dark:bg-slate-800 rounded-lg text-left max-w-lg w-full">
              <p className="text-sm font-mono text-red-600 dark:text-red-400 mb-2">
                {this.state.error.message}
              </p>
              {this.state.error.stack && (
                <pre className="text-xs text-slate-500 dark:text-slate-400 overflow-auto max-h-40 whitespace-pre-wrap">
                  {this.state.error.stack}
                </pre>
              )}
            </div>
          )}
        </div>
      );
    }

    return this.props.children;
  }
}
