package com.pharmacy.consultation.resource;

import com.pharmacy.consultation.dto.AskRequest;
import com.pharmacy.consultation.dto.AskResponse;
import com.pharmacy.consultation.dto.ConversationResponse;
import com.pharmacy.consultation.dto.ConversationSummaryResponse;
import com.pharmacy.consultation.dto.ReindexResponse;
import com.pharmacy.consultation.service.ConsultationService;
import com.pharmacy.consultation.service.KnowledgeIngestionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST Resource cung cấp các API tư vấn thuốc tự động và quản lý lịch sử hội thoại.
 */
@Path("/api/v1/consultations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Consultation Resource", description = "Hỏi đáp tư vấn thuốc tự động (Tầng 1 Template/RAG) và quản lý lịch sử hội thoại")
public class ConsultationResource {

    @Inject
    ConsultationService consultationService;

    @Inject
    KnowledgeIngestionService knowledgeIngestionService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/ask")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "SecurityScheme")
    @Operation(summary = "Hỏi đáp tư vấn thuốc tự động", description = "Nhận diện thuốc, phân loại ý định, trả lời theo mẫu hoặc phát hiện tình huống nguy kịch/nhạy cảm.")
    public Response ask(@Valid AskRequest request) {
        String userId = getUserId();
        AskResponse response = consultationService.ask(userId, request);
        return Response.ok(response).build();
    }

    @GET
    @Path("/conversations")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "SecurityScheme")
    @Operation(summary = "Lấy danh sách các phiên hội thoại của người dùng", description = "Chỉ trả về các phiên hội thoại do chính người dùng hiện tại tạo.")
    public Response getConversations() {
        String userId = getUserId();
        List<ConversationSummaryResponse> list = consultationService.getConversations(userId);
        return Response.ok(list).build();
    }

    @GET
    @Path("/conversations/{conversationId}")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "SecurityScheme")
    @Operation(summary = "Chi tiết một phiên hội thoại", description = "Xem lại toàn bộ tin nhắn hỏi - đáp trong phiên. Khách hàng chỉ xem được phiên của mình, ADMIN xem được tất cả.")
    public Response getConversation(@PathParam("conversationId") String conversationId) {
        String userId = getUserId();
        boolean isAdmin = isAdmin();
        ConversationResponse response = consultationService.getConversation(userId, conversationId, isAdmin);
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/conversations/{conversationId}")
    @RolesAllowed({"KHACH_HANG", "DUOC_SI", "ADMIN"})
    @SecurityRequirement(name = "SecurityScheme")
    @Operation(summary = "Xóa một phiên hội thoại", description = "Xóa phiên hội thoại khỏi lịch sử. Chỉ chủ sở hữu hoặc ADMIN mới có quyền xóa.")
    public Response deleteConversation(@PathParam("conversationId") String conversationId) {
        String userId = getUserId();
        boolean isAdmin = isAdmin();
        consultationService.deleteConversation(userId, conversationId, isAdmin);
        return Response.noContent().build();
    }

    @POST
    @Path("/admin/reindex")
    @RolesAllowed("ADMIN")
    @SecurityRequirement(name = "SecurityScheme")
    @Operation(summary = "Nạp lại toàn bộ tri thức thuốc từ medicine-service (Chỉ ADMIN)", description = "Gọi REST Client sang medicine-service để cập nhật kiến thức thuốc vào MongoDB và cache RAM.")
    public Response reindex() {
        KnowledgeIngestionService.ReindexResult result = knowledgeIngestionService.reindex();
        ReindexResponse response = new ReindexResponse(
                "SUCCESS",
                "Reindexed successfully",
                result.totalMedicines(),
                result.totalChunks()
        );
        return Response.ok(response).build();
    }

    private String getUserId() {
        return jwt.getSubject();
    }

    private boolean isAdmin() {
        return jwt.getGroups() != null && jwt.getGroups().contains("ADMIN");
    }
}
