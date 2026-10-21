package com.pharmacy.prescription.dto;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.ws.rs.core.MediaType;

/**
 * Form multipart nhận file ảnh đơn thuốc và ghi chú của khách hàng khi gửi đơn
 */
public class PrescriptionUploadForm {

    @RestForm("image")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public FileUpload image;

    @RestForm("notes")
    @PartType(MediaType.TEXT_PLAIN)
    public String notes;
}

