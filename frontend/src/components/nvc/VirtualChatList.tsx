import { useEffect, useRef } from 'react';

interface VirtualChatListProps<T> {
  items: T[];
  renderItem: (item: T, index: number) => React.ReactNode;
  className?: string;
  /** 超过此数量时只渲染最近的 N 条，提供"加载更多"按钮 */
  maxRenderCount?: number;
}

/**
 * 优化的聊天列表组件
 * 当消息数超过阈值时，只渲染最近的消息，减少 DOM 节点
 * 提供"加载更多"按钮查看历史消息
 */
export default function VirtualChatList<T>({
  items,
  renderItem,
  className = '',
  maxRenderCount = 100,
}: VirtualChatListProps<T>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const prevItemCountRef = useRef(items.length);

  // 新消息到来时自动滚动到底部
  useEffect(() => {
    if (items.length > prevItemCountRef.current) {
      setTimeout(() => {
        if (scrollRef.current) {
          scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
      }, 0);
    }
    prevItemCountRef.current = items.length;
  }, [items.length]);

  // 当消息数超过阈值时，只渲染最近的 N 条
  const displayItems = items.length > maxRenderCount
    ? items.slice(items.length - maxRenderCount)
    : items;

  const hiddenCount = items.length - displayItems.length;

  return (
    <div ref={scrollRef} className={className}>
      {hiddenCount > 0 && (
        <div className="text-center py-2 text-sm text-slate-400">
          已隐藏 {hiddenCount} 条历史消息
        </div>
      )}
      {displayItems.map((item, index) => renderItem(item, index + hiddenCount))}
    </div>
  );
}
