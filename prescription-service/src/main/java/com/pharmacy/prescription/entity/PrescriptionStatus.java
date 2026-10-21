package com.pharmacy.prescription.entity;

/**
 * Trạng thái của đơn thuốc:
 * - CHO_DUYET: Đơn mới được khách tải lên, chờ dược sĩ tiếp nhận xử lý
 * - DA_DUYET: Đơn hợp lệ đã được dược sĩ phê duyệt danh mục thuốc & số lượng cho phép
 * - TU_CHOI: Đơn bị dược sĩ từ chối (bắt buộc kèm lý do)
 */
public enum PrescriptionStatus {
    CHO_DUYET,
    DA_DUYET,
    TU_CHOI
}

