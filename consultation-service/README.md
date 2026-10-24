# Dịch Vụ Tư Vấn Thuốc Tự Động (consultation-service)

Dịch vụ vi mô đảm nhiệm chức năng tư vấn thuốc thông minh kết hợp 2 tầng (**Tầng 1: Template & Rule-based** và **Tầng 2: AI RAG + LLM**) cùng quản lý lịch sử hội thoại cho hệ thống **Nhà thuốc trực tuyến**, chạy trên framework Quarkus, kết nối MongoDB `consultation_db`, cổng **8088**.

---

## 1. Kiến Trúc Hai Tầng (Two-Tier Architecture)

```text
[Câu hỏi người dùng] ──> [Chuẩn hóa Text Normalizer] ──> [Bộ lọc an toàn Safety Guard]
                                                              │ (Nếu an toàn)
                                                              ▼
                                                   [Nhận diện thuốc & Ý định]
                                                              │
                       ┌──────────────────────────────────────┴──────────────────────────────────────┐
                       ▼                                                                             ▼
               [TẦNG 1: Điền Mẫu]                                                          [TẦNG 2: RAG + LLM]
      - Câu hỏi rõ 1 thuốc & rõ ý định                                            - Câu hỏi mở, so sánh, không rõ thuốc
      - Trả lời bằng mẫu trong templates.json                                     - Embedding câu hỏi + Cosine Search trên RAM
      - Thời gian phản hồi < 10ms, không tốn AI                                   - Tìm Top-K đoạn (ngưỡng tương đồng >= 0.65)
      - Hoạt động 100% không cần Internet/API Key                                 - Kiểm tra Cache MongoDB trước khi gọi LLM
                                                                                  - Prompt hệ thống versioned, temperature = 0
                                                                                  - Chế độ trích đoạn dự phòng khi AI lỗi
```

- **Framework**: Quarkus 3.39.4 (Java 21).
- **Cơ sở dữ liệu**: MongoDB (`consultation_db`) qua `quarkus-mongodb-panache`.
  - `knowledge_chunks`: Lưu trữ các đoạn tri thức thuốc chuẩn hóa kèm vector nhúng `embedding`.
  - `conversations`: Lưu trữ lịch sử hỏi đáp của từng người dùng (`userId`, `conversationId`).
  - `rag_caches`: Lưu đệm câu trả lời RAG Tầng 2 theo khóa băm SHA-256 `(intent + sortedMedicineIds + chunksHash)`.
- **Bảo mật & Phân quyền**: MicroProfile SmallRye JWT (`RS256`), hỗ trợ 3 vai trò: `KHACH_HANG`, `DUOC_SI`, `ADMIN`.
- **Giao tiếp liên service**: `quarkus-rest-client-jackson` gọi sang `medicine-service` (`http://localhost:8081`).
- **Khả năng chịu lỗi cao (Resilience)**:
  - Nếu `medicine-service` chưa bật: Không làm crash service khi khởi động.
  - Nếu dịch vụ AI (LLM / Embedding) bị timeout, hết hạn mức hoặc lỗi: Tự động chuyển sang **Chế độ trích đoạn (Extractive Fallback)**, trả về các đoạn tri thức trực tiếp kèm nguồn, **tuyệt đối không trả lỗi HTTP 500**.
- **Lưu trữ & Khởi động Vector**: Vector sau khi tạo từ `reindex` được lưu bền vững vào MongoDB `knowledge_chunks`. Khi service khởi động lại, nạp sẵn vector từ MongoDB lên RAM để tính toán; **KHÔNG gọi lại API embedding khi khởi động lại server**.

---

## 2. Cấu Hình Biến Môi Trường AI Tầng 2 (OpenAI-Compatible)

Hệ thống hỗ trợ chuẩn API OpenAI-compatible, cho phép kết nối linh hoạt tới OpenAI hoặc các nền tảng chạy mô hình cục bộ (Ollama, vLLM, LocalAI) hoàn toàn bằng biến môi trường mà không cần sửa code:

| Tên biến môi trường | Giá trị mặc định | Mô tả & Hướng dẫn sử dụng |
| :--- | :--- | :--- |
| `CONSULTATION_AI_BASE_URL` | `http://localhost:11434/v1` | URL gốc của dịch vụ AI (ví dụ `https://api.openai.com/v1` hoặc Ollama cục bộ) |
| `CONSULTATION_AI_API_KEY` | *(để trống)* | Khóa bí mật API (Bearer Token). Để trống khi dùng Ollama/LocalAI |
| `CONSULTATION_AI_CHAT_MODEL` | `gpt-4o-mini` | Tên mô hình LLM (ví dụ `gpt-4o-mini`, `qwen2.5:7b-instruct`, `llama-3.1-8b`) |
| `CONSULTATION_AI_EMBEDDING_MODEL` | `text-embedding-3-small` | Tên mô hình vector nhúng hỗ trợ tiếng Việt (ví dụ `text-embedding-3-small`, `bge-m3`) |
| `CONSULTATION_AI_TIMEOUT_SECONDS` | `10` | Thời gian timeout khi gọi API AI (giây) |

### Khuyến nghị lựa chọn mô hình hỗ trợ tiếng Việt:
1. **Mô hình Embedding**:
   - Điện toán đám mây: `text-embedding-3-small` (OpenAI - hỗ trợ đa ngôn ngữ và tiếng Việt rất tốt, chi phí cực thấp).
   - Mô hình cục bộ qua Ollama/vLLM: `bge-m3` (BAAI) hoặc `vinai/phobert-base-v2` (chuyên sâu xử lý tiếng Việt).
2. **Mô hình LLM**:
   - Điện toán đám mây: `gpt-4o-mini` (nhanh, rẻ, tuân thủ prompt hệ thống nghiêm ngặt).
   - Mô hình cục bộ qua Ollama: `qwen2.5:7b-instruct` hoặc `llama-3.1:8b-instruct`.

---

## 3. Cách Khởi Động & Chạy Kiểm Thử

### Yêu cầu tiên quyết
Đảm bảo container MongoDB đang chạy trên máy (port 27017):
```bash
docker compose up -d pharmacy-mongodb
```

### Chạy chế độ phát triển (Dev mode)
```bash
cd consultation-service
./mvnw quarkus:dev
```
- Swagger UI: [http://localhost:8088/q/swagger-ui/](http://localhost:8088/q/swagger-ui/)
- OpenAPI Spec: [http://localhost:8088/q/openapi](http://localhost:8088/q/openapi)

### Chạy bộ kiểm thử tự động (20 Test Cases)
```bash
./mvnw test
```

---

## 4. Danh Sách API Chính

| Method | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/consultations/ask` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Đặt câu hỏi tư vấn thuốc (Tầng 1 & Tầng 2 AI RAG) |
| `GET` | `/api/v1/consultations/conversations` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Danh sách tóm tắt phiên hội thoại của người dùng |
| `GET` | `/api/v1/consultations/conversations/{id}` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Chi tiết một phiên hội thoại (người dùng chỉ xem của mình) |
| `DELETE` | `/api/v1/consultations/conversations/{id}` | `KHACH_HANG`, `DUOC_SI`, `ADMIN` | Xóa phiên hội thoại (người dùng chỉ xóa của mình) |
| `POST` | `/api/v1/consultations/admin/reindex` | `ADMIN` | Nạp lại tri thức thuốc, sinh lại vector và xóa toàn bộ RAG cache |
| `GET` | `/api/v1/dev/tokens` | Public *(Chỉ bật ở profile dev/test)* | Tiện ích sinh JWT token thử nghiệm |

---

## 5. Giới Hạn Đã Biết (Known Limitations)

- **Tính tương đối của LLM ở câu hỏi mở**: Mô hình ngôn ngữ lớn (LLM) không bảo đảm nguyên văn giống hệt từng chữ khi trả lời các câu hỏi mở diễn đạt khác nhau. Tuy nhiên, hệ thống bảo đảm **luôn truy xuất cùng một tập nguồn tri thức chính xác** và trả về câu trả lời đồng nhất nhờ cơ chế Cache Tầng 2 trong MongoDB.
- **Câu hỏi phổ biến chuẩn hóa**: Được giải quyết 100% bằng Tầng 1 (Template-based), trả về cùng một câu trả lời chính xác và không phụ thuộc dịch vụ ngoài.
