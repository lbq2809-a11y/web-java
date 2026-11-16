import { Link } from 'react-router-dom';
import { ShieldAlert, Cross } from 'lucide-react';

export function Footer() {
  return (
    <footer className="border-t border-[#E2E8F0] bg-white">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid gap-8 md:grid-cols-3">
          <div>
            <div className="flex items-center gap-2">
              <span className="flex h-7 w-7 items-center justify-center rounded-md bg-[#0F766E] text-white">
                <Cross className="h-4 w-4" strokeWidth={2.5} />
              </span>
              <span className="text-sm font-bold text-[#0F172A]">An Tâm Pharmacy</span>
            </div>
            <p className="mt-2.5 text-sm leading-relaxed text-[#475569]">
              Hệ thống nhà thuốc trực tuyến chuẩn thực hành tốt GPP, hỗ trợ tra cứu thông tin thuốc minh bạch và tư vấn dược phẩm thông minh.
            </p>
          </div>

          <div className="space-y-2 text-sm">
            <h4 className="font-semibold text-[#0F172A]">Điều hướng nhanh</h4>
            <ul className="space-y-1.5 text-[#475569]">
              <li>
                <Link to="/medicines" className="hover:text-[#0F766E]">
                  Danh mục thuốc OTC & ETC
                </Link>
              </li>
              <li>
                <Link to="/consultation" className="hover:text-[#0F766E]">
                  Trợ lý tư vấn thuốc AI
                </Link>
              </li>
              <li>
                <Link to="/profile" className="hover:text-[#0F766E]">
                  Hồ sơ tài khoản
                </Link>
              </li>
            </ul>
          </div>

          <div className="rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-4">
            <div className="flex items-start gap-2.5">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-[#0F766E]" />
              <div className="text-xs leading-relaxed text-[#0F172A]">
                <p className="font-semibold text-[#0F766E]">
                  Miễn trừ trách nhiệm y tế
                </p>
                <p className="mt-1 text-[#475569]">
                  Thông tin thuốc và nội dung trả lời từ trợ lý AI chỉ mang tính chất tham khảo, không thay thế cho chẩn đoán, kê đơn hoặc tư vấn trực tiếp từ bác sĩ và dược sĩ chuyên môn.
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-8 border-t border-[#E2E8F0] pt-4 text-center text-xs text-[#475569]">
          © {new Date().getFullYear()} An Tâm Pharmacy — Đồ án Microservices Quarkus & React.
        </div>
      </div>
    </footer>
  );
}
