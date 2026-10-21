package com.pharmacy.prescription.service;

import com.pharmacy.prescription.exception.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Quản lý lưu trữ tệp ảnh đơn thuốc trên MinIO S3 Object Storage
 */
@ApplicationScoped
public class MinioStorageService {

    private static final Logger LOG = Logger.getLogger(MinioStorageService.class);

    // Giới hạn dung lượng ảnh tải lên tối đa 5MB (5,242,880 bytes)
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @ConfigProperty(name = "minio.endpoint", defaultValue = "http://localhost:9000")
    String endpoint;

    @ConfigProperty(name = "minio.access-key", defaultValue = "minioadmin")
    String accessKey;

    @ConfigProperty(name = "minio.secret-key", defaultValue = "minioadmin")
    String secretKey;

    @ConfigProperty(name = "minio.bucket", defaultValue = "prescriptions")
    String bucketName;

    private MinioClient minioClient;

    /**
     * Khởi tạo bucket tự động khi service khởi động nếu bucket chưa tồn tại
     */
    void onStart(@Observes StartupEvent ev) {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                LOG.infof("Đã khởi tạo thành công MinIO bucket: %s", bucketName);
            } else {
                LOG.infof("MinIO bucket '%s' đã sẵn sàng", bucketName);
            }
        } catch (Exception e) {
            LOG.warnf("Không thể kết nối hoặc khởi tạo MinIO bucket '%s' khi khởi động (MinIO có thể chưa chạy): %s",
                    bucketName, e.getMessage());
        }
    }

    /**
     * Lấy client MinIO đã khởi tạo (hoặc lazy init nếu chưa có)
     */
    private MinioClient getClient() {
        if (this.minioClient == null) {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }
        return this.minioClient;
    }

    public record UploadResult(String objectKey, String contentType, long sizeBytes) {}

    /**
     * Kiểm tra hợp lệ (kích thước <= 5MB, định dạng JPG/PNG qua magic bytes) và tải tệp lên MinIO
     */
    public UploadResult uploadPrescriptionImage(FileUpload fileUpload) {
        if (fileUpload == null || fileUpload.filePath() == null) {
            throw ApiException.badRequest("EMPTY_FILE", "Vui lòng chọn tệp ảnh đơn thuốc để tải lên");
        }

        Path tempPath = fileUpload.filePath();
        long fileSize;
        try {
            fileSize = Files.size(tempPath);
        } catch (Exception e) {
            throw ApiException.badRequest("FILE_READ_ERROR", "Không thể đọc thông tin tệp tải lên: " + e.getMessage());
        }

        // Kiểm tra dung lượng <= 5MB
        if (fileSize > MAX_FILE_SIZE) {
            throw ApiException.badRequest(
                    "FILE_SIZE_EXCEEDED",
                    "Dung lượng tệp vượt quá giới hạn cho phép (tối đa 5MB, kích thước tệp hiện tại: " + fileSize + " bytes)"
            );
        }

        if (fileSize < 4) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "Tệp tải lên không hợp lệ hoặc bị rỗng");
        }

        // Kiểm tra magic bytes để ngăn chặn tấn công giả mạo đuôi tệp
        byte[] headerBytes = new byte[8];
        try (InputStream is = Files.newInputStream(tempPath)) {
            int read = is.read(headerBytes);
            if (read < 4) {
                throw ApiException.badRequest("INVALID_FILE_TYPE", "Không thể xác minh định dạng tệp tải lên");
            }
        } catch (Exception e) {
            throw ApiException.badRequest("FILE_READ_ERROR", "Lỗi đọc nội dung tệp: " + e.getMessage());
        }

        String extension;
        String contentType;

        // JPG: FF D8 FF
        if ((headerBytes[0] & 0xFF) == 0xFF && (headerBytes[1] & 0xFF) == 0xD8 && (headerBytes[2] & 0xFF) == 0xFF) {
            extension = ".jpg";
            contentType = "image/jpeg";
        }
        // PNG: 89 50 4E 47
        else if ((headerBytes[0] & 0xFF) == 0x89 && headerBytes[1] == 0x50 && headerBytes[2] == 0x4E && headerBytes[3] == 0x47) {
            extension = ".png";
            contentType = "image/png";
        } else {
            throw ApiException.badRequest(
                    "INVALID_FILE_TYPE",
                    "Chỉ chấp nhận tệp định dạng hình ảnh JPG hoặc PNG (kiểm tra theo magic bytes)"
            );
        }

        LocalDate now = LocalDate.now();
        String objectKey = String.format("prescriptions/%d/%02d/%s%s",
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), extension);

        try (InputStream is = Files.newInputStream(tempPath)) {
            getClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(is, fileSize, -1)
                            .contentType(contentType)
                            .build()
            );
            LOG.infof("Đã lưu ảnh đơn thuốc vào MinIO: bucket=%s, key=%s, size=%d bytes",
                    bucketName, objectKey, fileSize);
        } catch (Exception e) {
            LOG.error("Lỗi khi tải tệp lên MinIO S3", e);
            throw new ApiException("STORAGE_ERROR", "Lỗi lưu trữ tệp ảnh đơn thuốc: " + e.getMessage(),
                    jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        }

        return new UploadResult(objectKey, contentType, fileSize);
    }

    /**
     * Tải luồng dữ liệu nhị phân (InputStream) của ảnh từ MinIO
     */
    public InputStream getPrescriptionImageStream(String objectKey) {
        try {
            return getClient().getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            LOG.errorf("Lỗi khi tải ảnh từ MinIO với objectKey=%s: %s", objectKey, e.getMessage());
            throw ApiException.notFound("Không thể tải tệp ảnh từ kho lưu trữ: " + e.getMessage());
        }
    }
}

