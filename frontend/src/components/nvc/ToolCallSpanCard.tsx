import { useState } from 'react';
import type { AgentSpan, HookRecord, ToolCallInput, ToolCallOutput } from '../../types/trace';

interface Props {
  span: AgentSpan;
  maxDuration: number;
}

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: 'bg-green-100 border-green-300 text-green-800',
  DEGRADED: 'bg-yellow-100 border-yellow-300 text-yellow-800',
  FAILED: 'bg-red-100 border-red-300 text-red-800',
};

const DECISION_badge: Record<string, { bg: string; text: string }> = {
  SKIP: { bg: 'bg-yellow-100', text: 'text-yellow-800' },
  CONTINUE: { bg: 'bg-green-100', text: 'text-green-800' },
  MODIFIED: { bg: 'bg-blue-100', text: 'text-blue-800' },
  PASSTHROUGH: { bg: 'bg-gray-100', text: 'text-gray-800' },
  ERROR: { bg: 'bg-red-100', text: 'text-red-800' },
};

export default function ToolCallSpanCard({ span, maxDuration }: Props) {
  const [expanded, setExpanded] = useState(false);

  const widthPercent = maxDuration > 0 ? Math.max((span.durationMs / maxDuration) * 100, 2) : 2;
  const statusColor = STATUS_COLORS[span.status] || STATUS_COLORS.SUCCESS;

  // 解析 payload
  const inputPayload = parseJson<ToolCallInput>(span.inputPayload);
  const outputPayload = parseJson<ToolCallOutput>(span.outputPayload);
  const metadata = span.metadata as Record<string, unknown> | undefined;

  const toolName = inputPayload?.toolName || 'unknown';
  const isSkipped = metadata?.skipped as boolean || false;
  const hookCount = metadata?.hookCount as number || 0;

  return (
    <div className="border rounded-lg overflow-hidden mb-2">
      {/* 头部 */}
      <div
        className={`flex items-center gap-3 px-4 py-2 cursor-pointer hover:bg-gray-50 ${statusColor}`}
        onClick={() => setExpanded(!expanded)}
      >
        <span className="text-sm font-medium w-32">🔧 工具调用</span>
        <span className="text-xs font-mono bg-blue-100 text-blue-800 px-2 py-0.5 rounded">
          {toolName}
        </span>

        {/* 状态标签 */}
        {isSkipped && (
          <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">
            跳过
          </span>
        )}

        {/* Hook 数量 */}
        {hookCount > 0 && (
          <span className="text-xs text-gray-500">
            {hookCount} 个 Hook
          </span>
        )}

        {/* 时间条 */}
        <div className="flex-1 h-4 bg-gray-200 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full ${
              span.status === 'FAILED' ? 'bg-red-400' :
              span.status === 'DEGRADED' ? 'bg-yellow-400' : 'bg-blue-400'
            }`}
            style={{ width: `${widthPercent}%` }}
          />
        </div>

        <span className="text-xs font-mono w-16 text-right">{span.durationMs}ms</span>
        <span className="text-xs">{expanded ? '▼' : '▶'}</span>
      </div>

      {/* 展开详情 */}
      {expanded && (
        <div className="px-4 py-3 bg-gray-50 border-t text-xs space-y-4">
          {/* 工具参数 */}
          {inputPayload?.arguments && (
            <div>
              <div className="font-medium text-gray-600 mb-2">📋 输入参数：</div>
              <div className="bg-white rounded border overflow-hidden">
                <ArgumentsTable arguments={inputPayload.arguments} />
              </div>
            </div>
          )}

          {/* 执行结果 */}
          {outputPayload && (
            <div>
              <div className="font-medium text-gray-600 mb-2">
                📤 执行结果：
                <span className={`ml-2 px-2 py-0.5 rounded ${
                  outputPayload.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                }`}>
                  {outputPayload.success ? '成功' : '失败'}
                </span>
              </div>
              <pre className="bg-white p-2 rounded border overflow-x-auto max-h-48">
                {formatResult(outputPayload.result)}
              </pre>
              {outputPayload.skipReason && (
                <div className="mt-2 text-yellow-600">
                  ⚠️ 跳过原因: {outputPayload.skipReason}
                </div>
              )}
            </div>
          )}

          {/* Hook 执行链路 */}
          {inputPayload?.hookChain && inputPayload.hookChain.length > 0 && (
            <div>
              <div className="font-medium text-gray-600 mb-2">🔗 Hook 执行链路：</div>
              <HookChainTimeline hooks={inputPayload.hookChain} totalDuration={span.durationMs} />
            </div>
          )}

          {/* 失败原因 */}
          {span.failureReason && (
            <div className="text-red-600">
              <strong>❌ 失败原因:</strong> {span.failureReason}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * 参数表格组件
 */
function ArgumentsTable({ arguments: args }: { arguments: string | Record<string, unknown> }) {
  let parsed: Record<string, unknown>;

  if (typeof args === 'string') {
    try {
      parsed = JSON.parse(args);
    } catch {
      return <pre className="p-2 text-xs">{args}</pre>;
    }
  } else {
    parsed = args;
  }

  const entries = Object.entries(parsed);

  if (entries.length === 0) {
    return <div className="p-2 text-gray-500">无参数</div>;
  }

  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="bg-gray-50">
          <th className="text-left px-3 py-1.5 font-medium text-gray-600">参数名</th>
          <th className="text-left px-3 py-1.5 font-medium text-gray-600">值</th>
        </tr>
      </thead>
      <tbody>
        {entries.map(([key, value]) => (
          <tr key={key} className="border-t">
            <td className="px-3 py-1.5 font-mono text-blue-600">{key}</td>
            <td className="px-3 py-1.5">
              <ValueDisplay value={value} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * 值显示组件（支持嵌套对象）
 */
function ValueDisplay({ value }: { value: unknown }) {
  if (value === null || value === undefined) {
    return <span className="text-gray-400">null</span>;
  }

  if (typeof value === 'string') {
    // 截断长字符串
    if (value.length > 100) {
      return <span className="font-mono">{value.substring(0, 100)}...</span>;
    }
    return <span className="font-mono">{value}</span>;
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return <span className="font-mono">{String(value)}</span>;
  }

  if (Array.isArray(value)) {
    return <span className="text-gray-500">[{value.length} 项]</span>;
  }

  if (typeof value === 'object') {
    return <span className="text-gray-500">{'{...}'}</span>;
  }

  return <span>{String(value)}</span>;
}

/**
 * Hook 链时间线组件
 */
function HookChainTimeline({ hooks, totalDuration }: { hooks: HookRecord[]; totalDuration: number }) {
  // 按 phase 分组
  const beforeHooks = hooks.filter(h => h.phase === 'before');
  const afterHooks = hooks.filter(h => h.phase === 'after');

  return (
    <div className="space-y-3">
      {/* before hooks */}
      {beforeHooks.length > 0 && (
        <div>
          <div className="text-xs text-gray-500 mb-1">执行前 Hook：</div>
          <div className="space-y-1">
            {beforeHooks.map((hook, index) => (
              <HookItem key={index} hook={hook} totalDuration={totalDuration} />
            ))}
          </div>
        </div>
      )}

      {/* 工具执行 */}
      <div className="flex items-center gap-2 py-1">
        <div className="w-2 h-2 bg-blue-500 rounded-full" />
        <span className="text-xs font-medium">工具执行</span>
      </div>

      {/* after hooks */}
      {afterHooks.length > 0 && (
        <div>
          <div className="text-xs text-gray-500 mb-1">执行后 Hook：</div>
          <div className="space-y-1">
            {afterHooks.map((hook, index) => (
              <HookItem key={index} hook={hook} totalDuration={totalDuration} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 单个 Hook 项
 */
function HookItem({ hook, totalDuration }: { hook: HookRecord; totalDuration: number }) {
  const badge = DECISION_badge[hook.decision] || DECISION_badge.PASSTHROUGH;
  const widthPercent = totalDuration > 0 ? Math.max((hook.durationMs / totalDuration) * 100, 2) : 2;

  return (
    <div className="flex items-center gap-2 pl-4">
      <div className="w-1.5 h-1.5 bg-gray-400 rounded-full" />
      <span className="text-xs font-mono w-32 truncate">{hook.hook}</span>
      <span className={`text-xs px-1.5 py-0.5 rounded ${badge.bg} ${badge.text}`}>
        {hook.decision}
      </span>
      <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className="h-full bg-gray-400 rounded-full"
          style={{ width: `${widthPercent}%` }}
        />
      </div>
      <span className="text-xs font-mono w-12 text-right">{hook.durationMs}ms</span>
      {hook.error && (
        <span className="text-xs text-red-500 truncate max-w-24" title={hook.error}>
          ⚠️
        </span>
      )}
    </div>
  );
}

/**
 * 格式化结果
 */
function formatResult(result: string): string {
  try {
    return JSON.stringify(JSON.parse(result), null, 2);
  } catch {
    return result;
  }
}

/**
 * 安全解析 JSON
 */
function parseJson<T>(json: string | undefined): T | null {
  if (!json) return null;
  try {
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
}
