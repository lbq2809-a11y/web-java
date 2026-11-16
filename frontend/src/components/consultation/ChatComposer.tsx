import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { AlertTriangle, Loader2, Send } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { cn } from '@/lib/utils';

// Khớp với consultation.question.max-length=500 trong consultation-service/application.properties
export const MAX_QUESTION_LENGTH = 500;

interface ChatComposerProps {
  onSend: (question: string) => Promise<void>;
  isSending: boolean;
  errorBanner: { title: string; message: string } | null;
  onDismissError: () => void;
}

export function ChatComposer({
  onSend,
  isSending,
  errorBanner,
  onDismissError,
}: ChatComposerProps) {
  const [question, setQuestion] = useState('');

  const trimmedLength = question.trim().length;
  const charCount = question.length;
  const isOverLimit = charCount > MAX_QUESTION_LENGTH;
  const canSubmit = trimmedLength > 0 && !isOverLimit && !isSending;

  const submitCurrent = async () => {
    if (!canSubmit) return;
    const text = question.trim();
    setQuestion('');
    await onSend(text);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await submitCurrent();
  };

  const handleKeyDown = async (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      await submitCurrent();
    }
  };

  return (
    <div className="border-t border-[#E2E8F0] bg-white px-4 py-3 sm:px-6">
      <div className="mx-auto max-w-3xl space-y-2.5">
        {errorBanner && (
          <Alert variant="destructive" className="py-3">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div className="flex-1">
              <AlertTitle>{errorBanner.title}</AlertTitle>
              <AlertDescription>{errorBanner.message}</AlertDescription>
            </div>
            <button
              type="button"
              onClick={onDismissError}
              className="text-xs font-semibold underline hover:opacity-80 cursor-pointer"
            >
              Đóng
            </button>
          </Alert>
        )}

        <form onSubmit={handleSubmit} className="space-y-2">
          <div className="relative">
            <Textarea
              value={question}
              onChange={(e) => {
                if (errorBanner) onDismissError();
                setQuestion(e.target.value);
              }}
              onKeyDown={handleKeyDown}
              disabled={isSending}
              rows={2}
              placeholder="Nhập câu hỏi về công dụng, liều dùng, tương tác thuốc... (Enter để gửi, Shift+Enter xuống dòng)"
              className="min-h-[68px] resize-none pr-14"
            />
            <div className="absolute bottom-2.5 right-2.5">
              <Button
                type="submit"
                size="icon"
                disabled={!canSubmit}
                aria-label="Gửi câu hỏi"
                title="Gửi câu hỏi"
              >
                {isSending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>

          <div className="flex items-center justify-between text-xs text-[#475569]">
            <span>
              {isSending
                ? 'AI đang phân tích dữ liệu dược phẩm...'
                : 'Hệ thống hỗ trợ tối đa 20 câu hỏi / phút'}
            </span>
            <span
              className={cn(
                'font-mono font-medium',
                isOverLimit
                  ? 'text-red-600'
                  : charCount >= 420
                    ? 'text-[#B45309]'
                    : 'text-[#64748B]',
              )}
            >
              {charCount}/{MAX_QUESTION_LENGTH} ký tự
            </span>
          </div>
        </form>
      </div>
    </div>
  );
}
