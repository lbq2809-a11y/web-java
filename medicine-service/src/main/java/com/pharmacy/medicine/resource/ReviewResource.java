package com.pharmacy.medicine.resource;

import com.pharmacy.medicine.dto.PagedResponse;
import com.pharmacy.medicine.dto.ReviewRequest;
import com.pharmacy.medicine.dto.ReviewResponse;
import com.pharmacy.medicine.service.ReviewService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST API quản lý đánh giá của người dùng cho từng thuốc (/api/v1/medicines/{medicineId}/reviews).
 */
@Path("/api/v1/medicines/{medicineId}/reviews")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reviews", description = "Đánh giá thuốc và tính điểm trung bình")
public class ReviewResource {

    @Inject
    ReviewService reviewService;

    @Inject
    JsonWebToken jwt;

    @GET
    @PermitAll
    @Operation(summary = "Liệt kê danh sách đánh giá của một thuốc (có phân trang, mới nhất trước)")
    public PagedResponse<ReviewResponse> listReviews(
            @PathParam("medicineId") String medicineId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size
    ) {
        return reviewService.listReviewsByMedicine(medicineId, page, size);
    }

    @POST
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Tạo đánh giá mới cho một thuốc (Yêu cầu đăng nhập)",
            description = "Mỗi người dùng (userId trong JWT sub) chỉ được đánh giá 1 lần cho mỗi thuốc. Điểm trung bình được tính lại bằng MongoDB Aggregation."
    )
    public Response createReview(
            @PathParam("medicineId") String medicineId,
            @Valid ReviewRequest request
    ) {
        String userId = jwt.getSubject();
        String fullName = jwt.getClaim("name");
        ReviewResponse created = reviewService.createReview(medicineId, userId, fullName, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}

