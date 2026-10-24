package com.pharmacy.recommendation.resource;

import com.pharmacy.recommendation.service.DataInitializer;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/v1/dev/seed")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile(anyOf = {"dev", "test"})
@Tag(name = "Dev Seed (Dev/Test Only)", description = "Nạp dữ liệu đơn hàng giả lập để tạo thống kê gợi ý")
public class DevSeedResource {

    @Inject
    DataInitializer dataInitializer;

    @POST
    @PermitAll
    @Operation(summary = "Kích hoạt nạp dữ liệu mẫu (~30 đơn hàng giả lập)")
    public Map<String, Object> seedData() {
        dataInitializer.seedDemoDataIfEmpty();
        return Map.of(
                "status", "SUCCESS",
                "message", "Đã nạp dữ liệu đơn hàng mẫu thành công vào recommendation_db"
        );
    }
}

