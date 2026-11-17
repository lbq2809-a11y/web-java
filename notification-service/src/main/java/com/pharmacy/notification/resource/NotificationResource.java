package com.pharmacy.notification.resource;

import com.pharmacy.notification.document.NotificationDocument;
import com.pharmacy.notification.service.NotificationService;

import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/notifications")
@Authenticated
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    NotificationService notificationService;

    @Inject
    JsonWebToken jwt;

    /**
     * GET /api/v1/notifications
     * Lấy danh sách thông báo của người đang đăng nhập.
     */
    @GET
    public List<NotificationResponse> getMyNotifications() {
        String userId = jwt.getSubject();

        return notificationService
                .getMyNotifications(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * GET /api/v1/notifications/unread-count
     * Đếm số thông báo chưa đọc.
     */
    @GET
    @Path("/unread-count")
    public UnreadCountResponse getUnreadCount() {
        String userId = jwt.getSubject();

        return new UnreadCountResponse(
                notificationService.countUnread(userId)
        );
    }

    /**
     * PUT /api/v1/notifications/{id}/read
     * Đánh dấu một thông báo đã đọc.
     */
    @PUT
    @Path("/{notificationId}/read")
    public Response markAsRead(
            @PathParam("notificationId") String notificationId
    ) {
        if (notificationId == null ||
                !ObjectId.isValid(notificationId)) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(
                            "INVALID_NOTIFICATION_ID",
                            "Mã thông báo không hợp lệ",
                            List.of()
                    ))
                    .build();
        }

        String userId = jwt.getSubject();

        Optional<NotificationDocument> result =
                notificationService.markAsRead(
                        notificationId,
                        userId
                );

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(
                            "NOTIFICATION_NOT_FOUND",
                            "Không tìm thấy thông báo",
                            List.of()
                    ))
                    .build();
        }

        return Response.ok(toResponse(result.get())).build();
    }

    /**
     * PUT /api/v1/notifications/read-all
     * Đánh dấu tất cả thông báo đã đọc.
     */
    @PUT
    @Path("/read-all")
    public Response markAllAsRead() {
        String userId = jwt.getSubject();

        long updatedCount =
                notificationService.markAllAsRead(userId);

        return Response.ok(
                new MarkAllReadResponse(updatedCount)
        ).build();
    }

    private NotificationResponse toResponse(
            NotificationDocument document
    ) {
        return new NotificationResponse(
                document.id.toHexString(),
                document.type,
                document.title,
                document.content,
                document.isRead,
                document.createdAt
        );
    }

    public record NotificationResponse(
            String notificationId,
            String type,
            String title,
            String content,
            boolean isRead,
            Instant createdAt
    ) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record MarkAllReadResponse(long updatedCount) {
    }

    public record ApiError(
            String code,
            String message,
            List<String> details
    ) {
    }
}