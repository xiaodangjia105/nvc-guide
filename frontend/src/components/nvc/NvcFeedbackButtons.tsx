import { useCallback, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ThumbsUp, ThumbsDown, X, Send } from 'lucide-react';
import { feedbackApi } from '../../api/feedback';
import type { FeedbackSource } from '../../api/feedback';
import { useUserId } from '../../hooks/useUserId';

interface NvcFeedbackButtonsProps {
  messageId: number;
  sessionId: number;
  messageSource: FeedbackSource;
  agentScene?: string;
  initialRating?: number | null;
  /** 反馈提交成功后的回调 */
  onFeedback?: (rating: number) => void;
}

/**
 * AI 回复的 👍/👎 反馈按钮
 * 点击后高亮，可展开输入文字评论
 */
export default function NvcFeedbackButtons({
  messageId,
  sessionId,
  messageSource,
  agentScene,
  initialRating,
  onFeedback,
}: NvcFeedbackButtonsProps) {
  const [userId] = useUserId();
  const [rating, setRating] = useState<number | null>(initialRating ?? null);
  const [showComment, setShowComment] = useState(false);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submitFeedback = useCallback(
    async (selectedRating: number, commentText?: string) => {
      setSubmitting(true);
      try {
        await feedbackApi.submit(userId, {
          sessionId,
          messageId,
          messageSource,
          agentScene: agentScene as any,
          rating: selectedRating,
          comment: commentText,
        });
        setRating(selectedRating);
        onFeedback?.(selectedRating);
        setShowComment(false);
        setComment('');
      } catch (err) {
        console.error('Feedback submit failed:', err);
      } finally {
        setSubmitting(false);
      }
    },
    [userId, sessionId, messageId, messageSource, agentScene, onFeedback]
  );

  const handleThumbsUp = useCallback(() => {
    submitFeedback(5);
  }, [submitFeedback]);

  const handleThumbsDown = useCallback(() => {
    // 如果已踩，展开评论输入；否则直接提交踩
    if (rating === 1) {
      setShowComment((prev) => !prev);
    } else {
      submitFeedback(1);
    }
  }, [rating, submitFeedback]);

  const handleSubmitComment = useCallback(() => {
    submitFeedback(1, comment.trim() || undefined);
  }, [comment, submitFeedback]);

  return (
    <div className="flex items-center gap-1 mt-1">
      {/* 👍 按钮 */}
      <button
        onClick={handleThumbsUp}
        disabled={submitting}
        className={`p-1 rounded-md transition-colors ${
          rating === 5
            ? 'text-green-500 bg-green-50 dark:bg-green-900/30'
            : 'text-slate-400 hover:text-green-500 hover:bg-slate-100 dark:hover:bg-slate-700'
        }`}
        title="有帮助"
      >
        <ThumbsUp className="w-3.5 h-3.5" />
      </button>

      {/* 👎 按钮 */}
      <button
        onClick={handleThumbsDown}
        disabled={submitting}
        className={`p-1 rounded-md transition-colors ${
          rating === 1
            ? 'text-red-500 bg-red-50 dark:bg-red-900/30'
            : 'text-slate-400 hover:text-red-500 hover:bg-slate-100 dark:hover:bg-slate-700'
        }`}
        title="需要改进"
      >
        <ThumbsDown className="w-3.5 h-3.5" />
      </button>

      {/* 评论输入框 */}
      <AnimatePresence>
        {showComment && (
          <motion.div
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 'auto' }}
            exit={{ opacity: 0, width: 0 }}
            className="flex items-center gap-1 ml-1"
          >
            <input
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSubmitComment();
                if (e.key === 'Escape') setShowComment(false);
              }}
              placeholder="说说哪里需要改进..."
              className="w-48 px-2 py-1 text-xs border border-slate-200 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-primary-500"
              autoFocus
            />
            <button
              onClick={handleSubmitComment}
              disabled={submitting}
              className="p-1 text-primary-500 hover:bg-primary-50 dark:hover:bg-primary-900/30 rounded-md"
              title="提交评论"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setShowComment(false)}
              className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-md"
              title="取消"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
