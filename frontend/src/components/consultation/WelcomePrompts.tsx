import { MessageSquarePlus, Sparkles, ShieldCheck } from 'lucide-react';

const SUGGESTED_QUESTIONS = [
  'Tôi bị sốt nhẹ kèm đau đầu thì có thuốc không kê đơn nào phù hợp?',
  'Augmentin dùng để làm gì và cần lưu ý gì?',
  'Đau dạ dày, ợ chua có thuốc không kê đơn nào hỗ trợ giảm đau?',
  'Siro ho và thuốc tiêu đờm nào phù hợp khi bị ho có đờm?',
];

interface WelcomePromptsProps {
  onSelectQuestion: (question: string) => void;
  disabled?: boolean;
}

export function WelcomePrompts({ onSelectQuestion, disabled }: WelcomePromptsProps) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center justify-center px-4 py-10 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]">
        <Sparkles className="h-7 w-7" />
      </div>

      <h1 className="mt-4 text-2xl font-bold tracking-tight text-[#0F172A]">
        Trợ lý Tư vấn Thuốc AI
      </h1>
      <p className="mt-2 max-w-lg text-sm leading-relaxed text-[#475569]">
        Tra cứu nhanh công dụng, liều dùng tham khảo, chống chỉ định và tương tác thuốc trực tiếp từ kho dữ liệu dược phẩm của nhà thuốc.
      </p>

      <div className="mt-3 inline-flex items-center gap-1.5 rounded-full border border-[#CCFBF1] bg-[#F0FDFA] px-3 py-1 text-xs font-medium text-[#0F766E]">
        <ShieldCheck className="h-3.5 w-3.5" />
        <span>Có trích dẫn nguồn thuốc chính xác ở mỗi câu trả lời</span>
      </div>

      <div className="mt-8 grid w-full gap-3 text-left sm:grid-cols-2">
        {SUGGESTED_QUESTIONS.map((question) => (
          <button
            key={question}
            type="button"
            disabled={disabled}
            onClick={() => onSelectQuestion(question)}
            className="group flex items-start gap-3 rounded-xl border border-[#E2E8F0] bg-white p-4 text-sm text-[#0F172A] transition-all hover:border-[#0F766E] hover:bg-[#F0FDFA]/50 disabled:opacity-50 cursor-pointer"
          >
            <MessageSquarePlus className="mt-0.5 h-4 w-4 shrink-0 text-[#0F766E] transition-transform group-hover:scale-110" />
            <span className="leading-snug font-medium">{question}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
