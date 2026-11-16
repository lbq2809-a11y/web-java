import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  BookOpen,
  Bot,
  Database,
  ExternalLink,
  Pill,
  Sparkles,
} from 'lucide-react';
import type { KnowledgeSource, SuggestedMedicineDto } from '@/types/consultation.types';
import { cn, formatTimeVi, translateKnowledgeSection } from '@/lib/utils';

export const DEFAULT_CLINICAL_DISCLAIMER =
  'Thông tin tư vấn từ trợ lý AI chỉ mang tính chất tham khảo, không thay thế cho chẩn đoán, chỉ định hay tư vấn của bác sĩ và chuyên gia y tế.';

interface AssistantMessageCardProps {
  content: string;
  tier: number;
  sources?: KnowledgeSource[];
  suggestedMedicines?: SuggestedMedicineDto[];
  disclaimer?: string;
  createdAt?: string;
}

/**
 * Render inline markdown an toàn: **in đậm** và `mã thuốc/liều lượng`
 */
function renderInlineFormatting(text: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, idx) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return (
        <strong key={idx} className="font-semibold text-[#0F172A]">
          {part.slice(2, -2)}
        </strong>
      );
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return (
        <code
          key={idx}
          className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-[#0F766E]"
        >
          {part.slice(1, -1)}
        </code>
      );
    }
    return <span key={idx}>{part}</span>;
  });
}

/**
 * Định dạng đoạn văn bản có hỗ trợ danh sách (- , * , 1. ) và đoạn văn.
 */
function FormattedAnswer({ content }: { content: string }) {
  const lines = content.split(/\r?\n/);
  const blocks: ReactNode[] = [];
  let listItems: { ordered: boolean; items: string[] } | null = null;

  const flushList = (keyPrefix: string) => {
    if (!listItems) return;
    if (listItems.ordered) {
      blocks.push(
        <ol key={`${keyPrefix}-ol`} className="my-2 list-decimal space-y-1.5 pl-5 text-sm leading-relaxed text-[#0F172A]">
          {listItems.items.map((item, i) => (
            <li key={i}>{renderInlineFormatting(item)}</li>
          ))}
        </ol>,
      );
    } else {
      blocks.push(
        <ul key={`${keyPrefix}-ul`} className="my-2 list-disc space-y-1.5 pl-5 text-sm leading-relaxed text-[#0F172A]">
          {listItems.items.map((item, i) => (
            <li key={i}>{renderInlineFormatting(item)}</li>
          ))}
        </ul>,
      );
    }
    listItems = null;
  };

  lines.forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (!line) {
      flushList(`line-${index}`);
      return;
    }

    const bulletMatch = /^[-*•]\s+(.*)$/.exec(line);
    const orderedMatch = /^\d+\.\s+(.*)$/.exec(line);

    if (bulletMatch) {
      if (listItems && listItems.ordered) {
        flushList(`line-${index}`);
      }
      if (!listItems) {
        listItems = { ordered: false, items: [] };
      }
      listItems.items.push(bulletMatch[1]);
      return;
    }

    if (orderedMatch) {
      if (listItems && !listItems.ordered) {
        flushList(`line-${index}`);
      }
      if (!listItems) {
        listItems = { ordered: true, items: [] };
      }
      listItems.items.push(orderedMatch[1]);
      return;
    }

    flushList(`line-${index}`);
    blocks.push(
      <p key={`p-${index}`} className="text-sm leading-relaxed text-[#0F172A]">
        {renderInlineFormatting(line)}
      </p>,
    );
  });

  flushList('end');

  return <div className="space-y-2">{blocks}</div>;
}

export function AssistantMessageCard({
  content,
  tier,
  sources = [],
  suggestedMedicines = [],
  disclaimer,
  createdAt,
}: AssistantMessageCardProps) {
  const isTier2 = tier === 2;

  return (
    <div className="flex items-start gap-3">
      <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]">
        <Bot className="h-4 w-4" />
      </div>

      <div className="min-w-0 flex-1 rounded-2xl border border-[#E2E8F0] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.03)] sm:p-5">
        {/* Header: Tên trợ lý + Huy hiệu phân tầng (Tier Badge) + thời gian */}
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-[#F1F5F9] pb-2.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-[#0F172A]">
              Trợ lý Dược lâm sàng
            </span>

            <span
              className={cn(
                'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold',
                isTier2
                  ? 'border-sky-200 bg-sky-50 text-sky-700'
                  : 'border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]',
              )}
            >
              {isTier2 ? (
                <>
                  <Sparkles className="h-3 w-3" />
                  <span>Tổng hợp AI từ dữ liệu thuốc</span>
                </>
              ) : (
                <>
                  <Database className="h-3 w-3" />
                  <span>Trả lời theo dữ liệu thuốc</span>
                </>
              )}
            </span>
          </div>

          {createdAt && (
            <span className="font-mono text-[11px] text-[#64748B]">
              {formatTimeVi(createdAt)}
            </span>
          )}
        </div>

        {/* 1. Nội dung trả lời định dạng rõ ràng */}
        <FormattedAnswer content={content} />

        {/* Gợi ý thuốc cùng hoạt chất (nếu có trong AskResponse) */}
        {suggestedMedicines.length > 0 && (
          <div className="mt-4 border-t border-[#F1F5F9] pt-3">
            <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-[#475569]">
              <Pill className="h-3.5 w-3.5 text-[#0F766E]" />
              <span>Thuốc liên quan có thể tham khảo:</span>
            </p>
            <div className="flex flex-wrap gap-2">
              {suggestedMedicines.map((med) => (
                <Link
                  key={med.medicineId}
                  to={`/medicines/${med.medicineId}`}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] px-2.5 py-1 text-xs font-medium text-[#0F172A] transition-colors hover:border-[#0F766E] hover:bg-[#F0FDFA] hover:text-[#0F766E]"
                >
                  <span>{med.name}</span>
                  <span
                    className={cn(
                      'rounded px-1 py-0.5 font-mono text-[10px] font-semibold',
                      med.prescriptionType === 'ETC'
                        ? 'bg-[#FEF3C7] text-[#B45309]'
                        : 'bg-[#CCFBF1] text-[#0F766E]',
                    )}
                  >
                    {med.prescriptionType}
                  </span>
                  <ExternalLink className="h-3 w-3 opacity-60" />
                </Link>
              ))}
            </div>
          </div>
        )}

        {/* 3. Thẻ nguồn trích dẫn (Source Citation Chips) */}
        {sources.length > 0 && (
          <div className="mt-4 rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-3">
            <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-[#0F766E]">
              <BookOpen className="h-3.5 w-3.5" />
              <span>Nguồn trích dẫn từ dữ liệu thuốc ({sources.length}):</span>
            </p>
            <div className="flex flex-wrap gap-2">
              {sources.map((src, idx) => (
                <Link
                  key={`${src.medicineId}-${src.section}-${idx}`}
                  to={`/medicines/${src.medicineId}`}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-[#CCFBF1] bg-white px-2.5 py-1.5 text-xs font-medium text-[#0F172A] shadow-2xs transition-colors hover:border-[#0F766E] hover:text-[#0F766E]"
                >
                  <span className="font-semibold">{src.medicineName}</span>
                  <span className="text-[#94A3B8]">•</span>
                  <span className="text-[#475569]">
                    {translateKnowledgeSection(src.section)}
                  </span>
                  <ExternalLink className="h-3 w-3 text-[#0F766E]" />
                </Link>
              ))}
            </div>
          </div>
        )}

        {/* 4. Dòng cảnh báo y tế (Disclaimer) */}
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] px-3 py-2 text-xs leading-relaxed text-[#475569]">
          <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#0F766E]" />
          <span>{disclaimer || DEFAULT_CLINICAL_DISCLAIMER}</span>
        </div>
      </div>
    </div>
  );
}
