package com.pharmacy.medicine.service;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.pharmacy.medicine.document.CategoryDocument;
import com.pharmacy.medicine.document.MedicineDocument;
import com.pharmacy.medicine.document.PrescriptionType;
import com.pharmacy.medicine.repository.CategoryRepository;
import com.pharmacy.medicine.repository.MedicineRepository;
import com.pharmacy.medicine.repository.ReviewRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.Document;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Khởi tạo các chỉ mục (MongoDB Indexes) và nạp dữ liệu mẫu (DEMO DATA) gồm 6 danh mục
 * và 30 thuốc phổ biến tại Việt Nam (đủ cả nhóm OTC và ETC) khi cơ sở dữ liệu còn trống.
 *
 * LƯU Ý: Toàn bộ thông tin thuốc dưới đây là DỮ LIỆU DEMO phục vụ đồ án môn học.
 * Mô tả y khoa được viết ngắn gọn, thận trọng và không thay thế chỉ định của bác sĩ/dược sĩ.
 */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    MedicineRepository medicineRepository;

    @Inject
    ReviewRepository reviewRepository;

    void onStart(@Observes StartupEvent ev) {
        try {
            ensureIndexes();
            seedDemoDataIfEmpty();
        } catch (Exception e) {
            LOG.warn("Không thể khởi tạo index hoặc dữ liệu mẫu khi khởi động (kiểm tra kết nối MongoDB): " + e.getMessage());
        }
    }

    /**
     * Khởi tạo các Index trên 3 collections: categories, medicines, reviews.
     */
    public void ensureIndexes() {
        // 1. Unique index trên categories.code
        categoryRepository.mongoCollection().createIndex(
                Indexes.ascending("code"),
                new IndexOptions().name("idx_categories_code_unique").unique(true)
        );

        // 2. Unique index trên medicines.code
        medicineRepository.mongoCollection().createIndex(
                Indexes.ascending("code"),
                new IndexOptions().name("idx_medicines_code_unique").unique(true)
        );

        // 3. Compound Text Index phục vụ tìm kiếm theo tên, hoạt chất, chỉ định
        Document textKeys = new Document("name", "text")
                .append("activeIngredient", "text")
                .append("indications", "text");
        Document weights = new Document("name", 10)
                .append("activeIngredient", 5)
                .append("indications", 2);
        medicineRepository.mongoCollection().createIndex(
                textKeys,
                new IndexOptions().name("idx_medicines_text_search").weights(weights)
        );

        // 4. Compound Index phục vụ lọc theo trạng thái, danh mục, loại thuốc (OTC/ETC) và giá
        medicineRepository.mongoCollection().createIndex(
                Indexes.ascending("active", "categoryId", "prescriptionType", "price"),
                new IndexOptions().name("idx_medicines_filter")
        );

        // 5. Compound Unique Index trên reviews (medicineId, userId): mỗi user tối đa 1 review cho 1 thuốc
        reviewRepository.mongoCollection().createIndex(
                Indexes.ascending("medicineId", "userId"),
                new IndexOptions().name("idx_reviews_medicine_user_unique").unique(true)
        );

        // 6. Index liệt kê đánh giá theo thời gian mới nhất
        reviewRepository.mongoCollection().createIndex(
                Indexes.compoundIndex(Indexes.ascending("medicineId"), Indexes.descending("createdAt")),
                new IndexOptions().name("idx_reviews_medicine_created")
        );
    }

    /**
     * Nạp 6 danh mục và 30 thuốc DEMO (18 OTC, 12 ETC) khi collection còn trống.
     */
    public void seedDemoDataIfEmpty() {
        if (medicineRepository.count() > 0) {
            return;
        }

        LOG.info("Đang khởi tạo dữ liệu mẫu DEMO (6 danh mục và 30 thuốc OTC/ETC phổ biến tại Việt Nam)...");

        CategoryDocument catPain = getOrCreateCategory(
                "CAT_PAIN_FEVER",
                "Giảm đau - Hạ sốt - Kháng viêm",
                "[DEMO] Nhóm thuốc hỗ trợ giảm đau, hạ sốt và kháng viêm thông thường hoặc theo đơn bác sĩ."
        );
        CategoryDocument catAntibiotic = getOrCreateCategory(
                "CAT_ANTIBIOTIC",
                "Kháng sinh - Kháng khuẩn",
                "[DEMO] Nhóm thuốc điều trị nhiễm khuẩn, đa số thuộc nhóm thuốc kê đơn (ETC) cần tuân thủ chỉ định."
        );
        CategoryDocument catDigestive = getOrCreateCategory(
                "CAT_DIGESTIVE",
                "Tiêu hóa - Dạ dày - Gan mật",
                "[DEMO] Nhóm thuốc hỗ trợ điều trị rối loạn tiêu hóa, viêm loét dạ dày - tá tràng và bù nước điện giải."
        );
        CategoryDocument catRespiratory = getOrCreateCategory(
                "CAT_RESPIRATORY",
                "Hô hấp - Tai Mũi Họng - Dị ứng",
                "[DEMO] Nhóm thuốc giảm ho, long đờm, viêm mũi dị ứng và hỗ trợ đường hô hấp."
        );
        CategoryDocument catCardioMetabolic = getOrCreateCategory(
                "CAT_CARDIO_METABOLIC",
                "Tim mạch - Huyết áp - Tiểu đường",
                "[DEMO] Nhóm thuốc kiểm soát huyết áp, mỡ máu và đường huyết (chủ yếu là thuốc kê đơn ETC)."
        );
        CategoryDocument catVitamin = getOrCreateCategory(
                "CAT_VITAMIN_MINERAL",
                "Vitamin & Khoáng chất",
                "[DEMO] Nhóm thuốc bổ sung vitamin, khoáng chất thiết yếu hỗ trợ nâng cao sức đề kháng."
        );

        List<MedicineDocument> demoMedicines = List.of(
                // === 1. Nhóm Giảm đau - Hạ sốt - Kháng viêm (5 thuốc: 3 OTC, 2 ETC) ===
                buildMedicine("MED-OTC-001", "Hapacol 650 DHG", "Paracetamol 650mg", "DHG Pharma (Việt Nam)", "Hộp 10 vỉ x 10 viên",
                        new BigDecimal("48000.00"), PrescriptionType.OTC, catPain,
                        "[Dữ liệu demo] Hỗ trợ giảm đau nhẹ đến vừa (đau đầu, đau răng, đau cơ) và hạ sốt.",
                        "Quá mẫn với Paracetamol, người suy gan nặng hoặc thiếu hụt men G6PD.",
                        "Tham khảo tờ hướng dẫn sử dụng: Người lớn uống 1 viên/lần, cách nhau tối thiểu 4-6 giờ khi cần."),
                buildMedicine("MED-OTC-002", "Panadol Extra Đỏ", "Paracetamol 500mg, Caffeine 65mg", "GSK", "Hộp 15 vỉ x 12 viên",
                        new BigDecimal("235000.00"), PrescriptionType.OTC, catPain,
                        "[Dữ liệu demo] Hỗ trợ hạ sốt và giảm các cơn đau đầu, đau nửa đầu, đau nhức cơ xương.",
                        "Suy gan nặng, phụ nữ có thai hoặc đang cho con bú cần hỏi ý kiến nhân viên y tế.",
                        "Tham khảo hướng dẫn: Người lớn uống 1-2 viên mỗi 4-6 giờ, không quá 8 viên/24 giờ."),
                buildMedicine("MED-OTC-003", "Efferalgan 500mg Sủi", "Paracetamol 500mg", "UPSA (Pháp)", "Hộp 4 vỉ x 4 viên sủi",
                        new BigDecimal("54000.00"), PrescriptionType.OTC, catPain,
                        "[Dữ liệu demo] Điều trị triệu chứng đau từ nhẹ đến vừa và/hoặc sốt.",
                        "Suy tế bào gan, người mẫn cảm với Paracetamol.",
                        "Hòa tan hoàn toàn 1 viên trong cốc nước đầy trước khi uống, cách nhau tối thiểu 4 giờ."),
                buildMedicine("MED-ETC-001", "Celebrex 200mg", "Celecoxib 200mg", "Pfizer", "Hộp 3 vỉ x 10 viên",
                        new BigDecimal("390000.00"), PrescriptionType.ETC, catPain,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị triệu chứng thoái hóa khớp, viêm khớp dạng thấp ở người lớn theo đơn bác sĩ.",
                        "Tiền sử dị ứng sulfonamid, loét dạ dày tiến triển, suy tim nặng, bệnh mạch vành.",
                        "Chỉ dùng theo đúng liều lượng và thời gian ghi trên đơn thuốc của bác sĩ."),
                buildMedicine("MED-ETC-002", "Mobic 7.5mg", "Meloxicam 7.5mg", "Boehringer Ingelheim", "Hộp 2 vỉ x 10 viên",
                        new BigDecimal("195000.00"), PrescriptionType.ETC, catPain,
                        "[Dữ liệu demo - Thuốc kê đơn] Kháng viêm không steroid (NSAID) điều trị triệu chứng viêm khớp theo chỉ định chuyên khoa.",
                        "Phụ nữ có thai/cho con bú, loét tiêu hóa tiến triển, suy gan hoặc suy thận nặng.",
                        "Dùng đường uống trong bữa ăn theo đúng chỉ định trong đơn thuốc được duyệt."),

                // === 2. Nhóm Kháng sinh - Kháng khuẩn (5 thuốc: 5 ETC) ===
                buildMedicine("MED-ETC-003", "Augmentin 1g", "Amoxicillin 875mg, Clavulanic acid 125mg", "GSK", "Hộp 2 vỉ x 7 viên",
                        new BigDecimal("285000.00"), PrescriptionType.ETC, catAntibiotic,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị nhiễm khuẩn đường hô hấp, tiết niệu, da và mô mềm do vi khuẩn nhạy cảm.",
                        "Tiền sử dị ứng với kháng sinh nhóm Beta-lactam (Penicillin, Cephalosporin) hoặc vàng da do thuốc.",
                        "Uống vào đầu bữa ăn theo đúng liều lượng và đủ ngày trong đơn thuốc của bác sĩ."),
                buildMedicine("MED-ETC-004", "Klacid Forte 500mg", "Clarithromycin 500mg", "Abbott", "Hộp 2 vỉ x 10 viên",
                        new BigDecimal("360000.00"), PrescriptionType.ETC, catAntibiotic,
                        "[Dữ liệu demo - Thuốc kê đơn] Kháng sinh nhóm Macrolid điều trị nhiễm khuẩn hô hấp, da và phối hợp diệt H. pylori.",
                        "Quá mẫn với Macrolid, không dùng đồng thời với Terfenadin, Astemizol, Cisaprid.",
                        "Sử dụng theo đúng phác đồ và đơn kê của bác sĩ điều trị."),
                buildMedicine("MED-ETC-005", "Zinnat 500mg", "Cefuroxime 500mg", "GSK", "Hộp 1 vỉ x 10 viên",
                        new BigDecimal("245000.00"), PrescriptionType.ETC, catAntibiotic,
                        "[Dữ liệu demo - Thuốc kê đơn] Kháng sinh Cephalosporin thế hệ 2 điều trị nhiễm khuẩn tai mũi họng, phế quản, tiết niệu.",
                        "Quá mẫn với kháng sinh nhóm Cephalosporin.",
                        "Uống sau bữa ăn để tăng hấp thu, tuân thủ nghiêm ngặt liều dùng trong đơn thuốc."),
                buildMedicine("MED-ETC-006", "Zithromax 250mg", "Azithromycin 250mg", "Pfizer", "Hộp 1 vỉ x 6 viên",
                        new BigDecimal("230000.00"), PrescriptionType.ETC, catAntibiotic,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị nhiễm khuẩn đường hô hấp trên và dưới, nhiễm khuẩn da do vi khuẩn nhạy cảm.",
                        "Quá mẫn với Azithromycin, Erythromycin hoặc bất kỳ kháng sinh nhóm Macrolid nào.",
                        "Dùng theo đơn bác sĩ, uống đủ liệu trình ngay cả khi triệu chứng đã thuyên giảm."),
                buildMedicine("MED-ETC-007", "Ciprobay 500mg", "Ciprofloxacin 500mg", "Bayer", "Hộp 1 vỉ x 10 viên",
                        new BigDecimal("215000.00"), PrescriptionType.ETC, catAntibiotic,
                        "[Dữ liệu demo - Thuốc kê đơn] Kháng sinh nhóm Quinolon chỉ định trong nhiễm khuẩn tiết niệu, tiêu hóa phức tạp theo đơn.",
                        "Quá mẫn với Ciprofloxacin, phụ nữ mang thai, trẻ em đang tuổi tăng trưởng (trừ chỉ định đặc biệt).",
                        "Uống với nhiều nước theo đúng liều lượng được bác sĩ kê đơn."),

                // === 3. Nhóm Tiêu hóa - Dạ dày - Gan mật (6 thuốc: 5 OTC, 1 ETC) ===
                buildMedicine("MED-OTC-004", "Phosphalugel (Thuốc dạ dày chữ P)", "Aluminium phosphate gel 20% (12.38g)", "Pharmatis (Pháp)", "Hộp 26 gói x 20g",
                        new BigDecimal("118000.00"), PrescriptionType.OTC, catDigestive,
                        "[Dữ liệu demo] Hỗ trợ trung hòa acid dịch vị, giảm đau rát thượng vị, ợ chua trong viêm dạ dày.",
                        "Suy thận nặng hoặc mẫn cảm với Aluminium phosphate.",
                        "Uống 1-2 gói khi xuất hiện cơn đau hoặc sau bữa ăn 1-2 giờ theo hướng dẫn sử dụng."),
                buildMedicine("MED-OTC-005", "Gaviscon Dual Action", "Sodium alginate, Sodium bicarbonate, Calcium carbonate", "Reckitt Benckiser", "Hộp 24 gói x 10ml",
                        new BigDecimal("172000.00"), PrescriptionType.OTC, catDigestive,
                        "[Dữ liệu demo] Hỗ trợ giảm triệu chứng trào ngược dạ dày - thực quản như ợ nóng, khó tiêu do dư acid.",
                        "Mẫn cảm với bất kỳ thành phần nào của thuốc.",
                        "Lắc kỹ trước khi dùng, người lớn uống 1-2 gói sau bữa ăn và trước khi đi ngủ."),
                buildMedicine("MED-OTC-006", "Smecta Hương Cam", "Diosmectite 3g", "Ipsen (Pháp)", "Hộp 30 gói",
                        new BigDecimal("128000.00"), PrescriptionType.OTC, catDigestive,
                        "[Dữ liệu demo] Hỗ trợ điều trị triệu chứng tiêu chảy cấp và mạn tính, bảo vệ niêm mạc tiêu hóa.",
                        "Tắc ruột hoặc mẫn cảm với Diosmectite.",
                        "Hòa gói thuốc với nước ấm ngay trước khi uống, dùng cách xa các thuốc khác 2 giờ."),
                buildMedicine("MED-OTC-007", "Enterogermina 2 tỷ bào tử", "Bào tử Bacillus clausii kháng đa kháng sinh", "Sanofi", "Hộp 20 ống x 5ml",
                        new BigDecimal("165000.00"), PrescriptionType.OTC, catDigestive,
                        "[Dữ liệu demo] Hỗ trợ phòng và phục hồi hệ vi khuẩn đường ruột bị rối loạn khi dùng kháng sinh hoặc rối loạn tiêu hóa.",
                        "Quá mẫn với bất kỳ thành phần nào của chế phẩm.",
                        "Lắc đều ống trước khi uống trực tiếp, không được tiêm."),
                buildMedicine("MED-OTC-008", "Oresol 245 DHG", "Natri clorid, Natri citrat, Kali clorid, Glucose khan", "DHG Pharma (Việt Nam)", "Hộp 20 gói",
                        new BigDecimal("35000.00"), PrescriptionType.OTC, catDigestive,
                        "[Dữ liệu demo] Bù nước và chất điện giải trong các trường hợp tiêu chảy, sốt cao hoặc ra nhiều mồ hôi.",
                        "Vô niệu, giảm niệu, tắc ruột, thủng ruột.",
                        "Pha toàn bộ 1 gói vào đúng 200ml nước đun sôi để nguội (không pha với sữa hay nước ngọt)."),
                buildMedicine("MED-ETC-008", "Nexium Mups 40mg", "Esomeprazole 40mg", "AstraZeneca", "Hộp 2 vỉ x 7 viên",
                        new BigDecimal("350000.00"), PrescriptionType.ETC, catDigestive,
                        "[Dữ liệu demo - Thuốc kê đơn] Ức chế bơm proton (PPI) điều trị trào ngược dạ dày thực quản và loét dạ dày - tá tràng.",
                        "Quá mẫn với Esomeprazole hoặc các dẫn xuất Benzimidazol, không dùng kèm Nelfinavir.",
                        "Nuốt nguyên viên với nước (không nhai hoặc nghiền viên) theo đơn của bác sĩ."),

                // === 4. Nhóm Hô hấp - Tai Mũi Họng - Dị ứng (5 thuốc: 5 OTC) ===
                buildMedicine("MED-OTC-009", "Telfast HD 180mg", "Fexofenadine HCl 180mg", "Sanofi", "Hộp 1 vỉ x 10 viên",
                        new BigDecimal("88000.00"), PrescriptionType.OTC, catRespiratory,
                        "[Dữ liệu demo] Kháng histamin thế hệ mới hỗ trợ giảm triệu chứng viêm mũi dị ứng và nổi mề đay mạn tính.",
                        "Trẻ em dưới 12 tuổi hoặc mẫn cảm với Fexofenadine.",
                        "Người lớn và trẻ trên 12 tuổi uống 1 viên/lần/ngày với nước lọc (tránh uống cùng nước hoa quả)."),
                buildMedicine("MED-OTC-010", "Clarityne 10mg", "Loratadine 10mg", "Bayer", "Hộp 1 vỉ x 10 viên",
                        new BigDecimal("72000.00"), PrescriptionType.OTC, catRespiratory,
                        "[Dữ liệu demo] Hỗ trợ giảm các triệu chứng hắt hơi, sổ mũi, ngứa mũi, ngứa mắt do viêm mũi dị ứng.",
                        "Quá mẫn với Loratadine, trẻ nhỏ dưới 2 tuổi.",
                        "Người lớn uống 1 viên/lần/ngày theo hướng dẫn trên bao bì."),
                buildMedicine("MED-OTC-011", "Acemuc 200mg", "Acetylcysteine 200mg", "Sanofi", "Hộp 30 gói cốm",
                        new BigDecimal("82000.00"), PrescriptionType.OTC, catRespiratory,
                        "[Dữ liệu demo] Thuốc tiêu chất nhầy hỗ trợ làm loãng đờm trong viêm phế quản cấp và mạn tính.",
                        "Tiền sử hen phế quản, loét dạ dày tá tràng tiến triển, trẻ em dưới 2 tuổi.",
                        "Hòa tan gói thuốc trong nửa cốc nước, uống sau ăn theo hướng dẫn."),
                buildMedicine("MED-OTC-012", "Bisolvon 8mg", "Bromhexine HCl 8mg", "Sanofi", "Hộp 3 vỉ x 10 viên",
                        new BigDecimal("56000.00"), PrescriptionType.OTC, catRespiratory,
                        "[Dữ liệu demo] Hỗ trợ làm loãng dịch tiết phế quản, giúp khạc đờm dễ hơn trong bệnh lý hô hấp.",
                        "Mẫn cảm với Bromhexine; thận trọng ở người có tiền sử loét dạ dày.",
                        "Uống sau bữa ăn với nhiều nước theo hướng dẫn sử dụng."),
                buildMedicine("MED-OTC-013", "Siro Ho Prospan", "Cao khô lá thường xuân (Hedera helix)", "Engelhard Arzneimittel (Đức)", "Chai 100ml",
                        new BigDecimal("85000.00"), PrescriptionType.OTC, catRespiratory,
                        "[Dữ liệu demo] Thảo dược hỗ trợ giảm ho, làm dịu cổ họng và tiêu nhầy đường hô hấp.",
                        "Bất dung nạp Fructose hoặc mẫn cảm với chiết xuất lá thường xuân.",
                        "Lắc kỹ chai trước mỗi lần dùng, đong bằng cốc chia vạch kèm theo hộp."),

                // === 5. Nhóm Tim mạch - Huyết áp - Tiểu đường (4 thuốc: 4 ETC) ===
                buildMedicine("MED-ETC-009", "Amlor 5mg", "Amlodipine 5mg", "Pfizer", "Hộp 3 vỉ x 10 viên",
                        new BigDecimal("210000.00"), PrescriptionType.ETC, catCardioMetabolic,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị tăng huyết áp và dự phòng đau thắt ngực ổn định theo chỉ định bác sĩ.",
                        "Hạ huyết áp nặng, sốc tim, hẹp động mạch chủ nặng.",
                        "Dùng hàng ngày theo đúng liều lượng bác sĩ kê đơn, không tự ý ngưng thuốc đột ngột."),
                buildMedicine("MED-ETC-010", "Concor 5mg", "Bisoprolol fumarate 5mg", "Merck (Đức)", "Hộp 3 vỉ x 10 viên",
                        new BigDecimal("185000.00"), PrescriptionType.ETC, catCardioMetabolic,
                        "[Dữ liệu demo - Thuốc kê đơn] Chẹn thụ thể Beta-1 chọn lọc dùng trong tăng huyết áp, bệnh mạch vành và suy tim mạn.",
                        "Suy tim cấp, nhịp tim chậm xoang nặng, block nhĩ thất độ 2-3, hen phế quản nặng.",
                        "Uống vào buổi sáng theo đúng liều lượng trên đơn thuốc được duyệt."),
                buildMedicine("MED-ETC-011", "Glucophage XR 500mg", "Metformin HCl 500mg (phóng thích kéo dài)", "Merck", "Hộp 2 vỉ x 15 viên",
                        new BigDecimal("98000.00"), PrescriptionType.ETC, catCardioMetabolic,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị đái tháo đường tuýp 2 ở người lớn kết hợp chế độ ăn và vận động.",
                        "Suy thận nặng, nhiễm toan chuyển hóa cấp, suy gan, nghiện rượu.",
                        "Uống nguyên viên trong bữa ăn tối theo chỉ định của bác sĩ chuyên khoa nội tiết."),
                buildMedicine("MED-ETC-012", "Crestor 10mg", "Rosuvastatin 10mg", "AstraZeneca", "Hộp 2 vỉ x 14 viên",
                        new BigDecimal("435000.00"), PrescriptionType.ETC, catCardioMetabolic,
                        "[Dữ liệu demo - Thuốc kê đơn] Điều trị rối loạn lipid máu nguyên phát và dự phòng biến cố tim mạch.",
                        "Bệnh gan tiến triển, suy thận nặng, phụ nữ đang mang thai hoặc cho con bú.",
                        "Uống 1 lần/ngày vào bất kỳ thời điểm nào theo đúng đơn kê của bác sĩ."),

                // === 6. Nhóm Vitamin & Khoáng chất (5 thuốc: 5 OTC) ===
                buildMedicine("MED-OTC-014", "Berocca Performance Mango", "Vitamin nhóm B, Vitamin C, Canxi, Magnesi, Kẽm", "Bayer", "Tuýp 10 viên sủi",
                        new BigDecimal("92000.00"), PrescriptionType.OTC, catVitamin,
                        "[Dữ liệu demo] Bổ sung vitamin nhóm B, C và khoáng chất hỗ trợ giảm mệt mỏi, tăng sự tỉnh táo.",
                        "Suy thận nặng, tăng canxi máu, sỏi tiết niệu.",
                        "Hòa tan 1 viên sủi trong 200ml nước, uống 1 viên/ngày sau bữa ăn sáng."),
                buildMedicine("MED-OTC-015", "Upsa C 1000mg", "Acid ascorbic (Vitamin C) 1000mg", "UPSA (Pháp)", "Tuýp 10 viên sủi",
                        new BigDecimal("52000.00"), PrescriptionType.OTC, catVitamin,
                        "[Dữ liệu demo] Hỗ trợ điều trị tình trạng thiếu hụt Vitamin C và mệt mỏi tạm thời ở người lớn.",
                        "Sỏi thận oxalat, thiếu hụt G6PD, bệnh thừa sắt.",
                        "Hòa tan 1 viên trong nửa cốc nước, nên uống vào buổi sáng hoặc trưa."),
                buildMedicine("MED-OTC-016", "Magne-B6 Corbière", "Magnesium lactate dihydrate 470mg, Pyridoxine HCl (Vitamin B6) 5mg", "Sanofi", "Hộp 5 vỉ x 10 viên",
                        new BigDecimal("112000.00"), PrescriptionType.OTC, catVitamin,
                        "[Dữ liệu demo] Hỗ trợ điều trị các trường hợp thiếu Magnesi gây mỏi cơ, chuột rút, căng thẳng.",
                        "Suy thận nặng với độ thanh thải creatinin dưới 30ml/phút.",
                        "Chia liều uống 2-3 lần/ngày trong bữa ăn với nhiều nước theo tờ hướng dẫn."),
                buildMedicine("MED-OTC-017", "Enervon C", "Vitamin C 500mg, Vitamin B1, B2, B3, B5, B6, B12", "United International Pharma", "Hộp 10 vỉ x 10 viên",
                        new BigDecimal("115000.00"), PrescriptionType.OTC, catVitamin,
                        "[Dữ liệu demo] Bổ sung Vitamin C và phức hợp Vitamin B giúp nâng cao thể trạng khi suy nhược.",
                        "Mẫn cảm với bất kỳ thành phần nào của thuốc.",
                        "Người lớn uống 1 viên/ngày sau bữa ăn."),
                buildMedicine("MED-OTC-018", "Calcium Corbière Extra", "Calcium glubionate, Calcium lactobionate", "Sanofi", "Hộp 3 vỉ x 10 ống uống 10ml",
                        new BigDecimal("175000.00"), PrescriptionType.OTC, catVitamin,
                        "[Dữ liệu demo] Bổ sung Canxi cho người có nhu cầu canxi cao (trẻ đang lớn, phụ nữ mang thai/cho con bú, người cao tuổi).",
                        "Tăng canxi máu, tăng canxi niệu, sỏi canxi thận.",
                        "Bẻ hai đầu ống để uống dung dịch bên trong sau bữa ăn sáng hoặc trưa.")
        );

        medicineRepository.persist(demoMedicines);
        LOG.infof("Đã nạp thành công %d thuốc DEMO vào MongoDB medicine_db.", demoMedicines.size());
    }

    private CategoryDocument getOrCreateCategory(String code, String name, String description) {
        return categoryRepository.findByCode(code).orElseGet(() -> {
            CategoryDocument doc = new CategoryDocument();
            doc.code = code;
            doc.name = name;
            doc.description = description;
            doc.active = true;
            Instant now = Instant.now();
            doc.createdAt = now;
            doc.updatedAt = now;
            categoryRepository.persist(doc);
            return doc;
        });
    }

    private MedicineDocument buildMedicine(
            String code,
            String name,
            String activeIngredient,
            String manufacturer,
            String unit,
            BigDecimal price,
            PrescriptionType prescriptionType,
            CategoryDocument category,
            String indications,
            String contraindications,
            String dosage
    ) {
        MedicineDocument doc = new MedicineDocument();
        doc.code = code;
        doc.name = name;
        doc.activeIngredient = activeIngredient;
        doc.manufacturer = manufacturer;
        doc.unit = unit;
        doc.price = price;
        doc.imageUrl = "https://placehold.co/400x400?text=" + code;
        doc.indications = indications;
        doc.contraindications = contraindications;
        doc.dosage = dosage;
        doc.prescriptionType = prescriptionType;
        doc.categoryId = category.id.toHexString();
        doc.categoryName = category.name;
        doc.active = true;
        doc.averageRating = 0.0;
        doc.reviewCount = 0;
        Instant now = Instant.now();
        doc.createdAt = now;
        doc.updatedAt = now;
        return doc;
    }
}

