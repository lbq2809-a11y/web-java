import { useState } from 'react';
import { Check, Copy, QrCode } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';

interface QrPaymentBoxProps {
  qrContent: string;
}

export function QrPaymentBox({ qrContent }: QrPaymentBoxProps) {
  const [copiedQr, setCopiedQr] = useState(false);

  const handleCopyQrContent = async () => {
    try {
      await navigator.clipboard.writeText(qrContent);
      setCopiedQr(true);
      setTimeout(() => setCopiedQr(false), 1500);
    } catch {
      // Bỏ qua nếu trình duyệt chặn clipboard
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] p-3">
      <div className="flex items-center gap-1.5 text-xs font-semibold text-[#0F766E]">
        <QrCode className="h-4 w-4" />
        <span>Quét mã QR để thanh toán</span>
      </div>

      {/* Khung trắng bo tròn chứa mã QR SVG */}
      <div className="flex justify-center">
        <div className="inline-flex rounded-xl border border-[#E2E8F0] bg-white p-3 shadow-xs">
          <QRCodeSVG value={qrContent} size={180} level="M" includeMargin />
        </div>
      </div>

      {/* Chuỗi nội dung QR & nút sao chép bên dưới */}
      <div className="space-y-1.5">
        <p className="break-all rounded-lg border border-[#E2E8F0] bg-white p-2 font-mono text-xs text-[#0F172A]">
          {qrContent}
        </p>
        <div className="flex justify-end">
          <button
            type="button"
            onClick={() => void handleCopyQrContent()}
            className="inline-flex items-center gap-1 rounded-md border border-[#CCFBF1] bg-white px-2.5 py-1 text-[11px] font-medium text-[#0F766E] transition-colors hover:bg-[#CCFBF1]/40"
          >
            {copiedQr ? (
              <>
                <Check className="h-3.5 w-3.5" />
                <span>Đã chép</span>
              </>
            ) : (
              <>
                <Copy className="h-3.5 w-3.5" />
                <span>Sao chép</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
