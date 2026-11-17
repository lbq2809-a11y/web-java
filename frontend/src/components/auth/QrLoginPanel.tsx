import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AlertCircle,
  Check,
  Clock,
  Copy,
  Loader2,
  QrCode,
  RefreshCw,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { authApi } from '@/api/auth.api';
import { normalizeApiError } from '@/api/httpClient';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import type { AuthResponse, CreateQrSessionResponse } from '@/types/auth.types';

interface QrLoginPanelProps {
  onAuthenticated: (authRes: AuthResponse) => void;
}

const POLL_INTERVAL_MS = 2000;

function computeRemainingSeconds(expiresAt: string): number {
  const diffMs = new Date(expiresAt).getTime() - Date.now();
  return Math.max(0, Math.ceil(diffMs / 1000));
}

export function QrLoginPanel({ onAuthenticated }: QrLoginPanelProps) {
  const [session, setSession] = useState<CreateQrSessionResponse | null>(null);
  const [isCreating, setIsCreating] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isExpired, setIsExpired] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [copiedLink, setCopiedLink] = useState(false);
  const [sessionNonce, setSessionNonce] = useState(0);

  const onAuthenticatedRef = useRef(onAuthenticated);
  onAuthenticatedRef.current = onAuthenticated;

  const triggerNewSession = useCallback(() => {
    setSessionNonce((n) => n + 1);
  }, []);

  // Khởi tạo phiên QR mới khi mở tab hoặc khi bấm "Tạo mã mới"
  useEffect(() => {
    let cancelled = false;
    setIsCreating(true);
    setErrorMessage(null);
    setIsExpired(false);
    setSession(null);

    authApi
      .createQrSession()
      .then((created) => {
        if (cancelled) return;
        setSession(created);
        setRemainingSeconds(computeRemainingSeconds(created.expiresAt));
      })
      .catch((err) => {
        if (cancelled) return;
        const normalized = normalizeApiError(err);
        if (normalized.status === 429) {
          setErrorMessage(
            normalized.message ||
              'Bạn thao tác quá nhanh. Vui lòng chờ ít giây rồi tạo mã mới.',
          );
        } else {
          setErrorMessage(normalized.message || 'Không thể khởi tạo phiên đăng nhập QR.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsCreating(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [sessionNonce]);

  // Đếm ngược TTL trên giao diện
  useEffect(() => {
    if (!session || isExpired) return;

    const timerId = window.setInterval(() => {
      const next = computeRemainingSeconds(session.expiresAt);
      setRemainingSeconds(next);
      if (next <= 0) {
        setIsExpired(true);
        setErrorMessage('Phiên đăng nhập QR đã hết hạn. Vui lòng tạo mã mới.');
        window.clearInterval(timerId);
      }
    }, 1000);

    return () => {
      window.clearInterval(timerId);
    };
  }, [session, isExpired]);

  // Polling trạng thái phiên QR mỗi 2 giây
  useEffect(() => {
    if (!session || isExpired) return;

    let cancelled = false;
    let inFlight = false;

    const pollOnce = async () => {
      if (cancelled || inFlight) return;
      inFlight = true;
      try {
        const res = await authApi.getQrSessionStatus(
          session.sessionId,
          session.pollSecret,
        );
        if (cancelled) return;

        if (res.status === 'CONFIRMED' && res.accessToken && res.user) {
          window.clearInterval(intervalId);
          onAuthenticatedRef.current({
            accessToken: res.accessToken,
            tokenType: res.tokenType ?? 'Bearer',
            expiresIn: res.expiresIn ?? 3600,
            user: res.user,
          });
        }
      } catch (err) {
        if (cancelled) return;
        const normalized = normalizeApiError(err);
        window.clearInterval(intervalId);
        setIsExpired(true);
        if (normalized.status === 404) {
          setErrorMessage('Phiên đăng nhập QR đã hết hạn hoặc không còn tồn tại.');
        } else if (normalized.status === 429) {
          setErrorMessage(
            normalized.message ||
              'Quá nhiều lượt kiểm tra. Vui lòng tạo mã QR mới.',
          );
        } else {
          setErrorMessage(normalized.message);
        }
      } finally {
        inFlight = false;
      }
    };

    const intervalId = window.setInterval(() => {
      void pollOnce();
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [session, isExpired]);

  const confirmUrl = session
    ? `${window.location.origin}/qr-confirm?sessionId=${encodeURIComponent(session.sessionId)}`
    : '';

  const handleCopyLink = async () => {
    if (!confirmUrl) return;
    try {
      await navigator.clipboard.writeText(confirmUrl);
      setCopiedLink(true);
      window.setTimeout(() => setCopiedLink(false), 1500);
    } catch {
      // Bỏ qua nếu trình duyệt chặn clipboard
    }
  };

  if (isCreating) {
    return (
      <div className="flex flex-col items-center justify-center space-y-3 py-8 text-center">
        <Loader2 className="h-8 w-8 animate-spin text-[#0F766E]" />
        <p className="text-sm text-[#475569]">Đang tạo mã QR đăng nhập...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {errorMessage && (
        <Alert variant={isExpired ? 'warning' : 'destructive'}>
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <div className="flex-1">
            <AlertTitle>
              {isExpired ? 'Phiên QR không còn hiệu lực' : 'Không thể tạo mã QR'}
            </AlertTitle>
            <AlertDescription>{errorMessage}</AlertDescription>
          </div>
        </Alert>
      )}

      {session && !isExpired ? (
        <div className="flex flex-col items-center space-y-4">
          <div className="rounded-xl border border-[#E2E8F0] bg-white p-4 shadow-xs">
            <QRCodeSVG value={confirmUrl} size={188} level="M" includeMargin />
          </div>

          <div className="flex flex-wrap items-center justify-center gap-3 text-xs text-[#475569]">
            <span className="inline-flex items-center gap-1 rounded-full border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-1 font-mono font-semibold text-[#0F172A]">
              <QrCode className="h-3.5 w-3.5 text-[#0F766E]" />
              Phiên #{session.sessionId.slice(0, 8).toUpperCase()}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-1 font-mono font-semibold text-[#0F766E]">
              <Clock className="h-3.5 w-3.5" />
              Còn {remainingSeconds}s
            </span>
          </div>

          <p className="text-center text-xs leading-relaxed text-[#475569]">
            Quét mã QR bằng thiết bị đã đăng nhập, hoặc mở liên kết xác nhận dưới đây ở
            một tab trình duyệt đã đăng nhập để thử nghiệm:
          </p>

          <div className="w-full space-y-1.5 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-2.5">
            <p className="break-all font-mono text-[11px] text-[#0F172A]">
              {confirmUrl}
            </p>
            <div className="flex items-center justify-between gap-2">
              <span className="inline-flex items-center gap-1.5 text-[11px] text-[#0F766E]">
                <Loader2 className="h-3 w-3 animate-spin" />
                <span>Đang chờ xác nhận (tự kiểm tra mỗi 2s)...</span>
              </span>
              <button
                type="button"
                onClick={() => void handleCopyLink()}
                className="inline-flex shrink-0 items-center gap-1 rounded border border-[#E2E8F0] bg-white px-2 py-0.5 text-[11px] font-medium text-[#0F766E] hover:border-[#0F766E]"
              >
                {copiedLink ? (
                  <>
                    <Check className="h-3 w-3" />
                    <span>Đã chép</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3" />
                    <span>Sao chép</span>
                  </>
                )}
              </button>
            </div>
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={triggerNewSession}
            className="w-full"
          >
            <RefreshCw className="h-4 w-4" />
            <span>Tạo mã mới</span>
          </Button>
        </div>
      ) : (
        <Button type="button" onClick={triggerNewSession} className="w-full">
          <RefreshCw className="h-4 w-4" />
          <span>Tạo mã mới</span>
        </Button>
      )}
    </div>
  );
}
