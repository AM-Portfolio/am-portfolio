package com.am.common.amcommondata.document.stress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stress_scenarios")
public class StressScenarioDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("scenario_code")
    private String scenarioCode;

    @Field("scenario_name")
    private String scenarioName;

    @Field("scenario_type")
    private String scenarioType; // UNIFORM, SECTOR, MULTI_SECTOR

    @Field("shock_pct")
    private Double shockPct;

    @Field("target_sector")
    private String targetSector;

    @Field("sector_shocks")
    private Map<String, Double> sectorShocks;

    @Field("residual_shock")
    private Double residualShock;

    @Field("description")
    private String description;

    @Field("is_active")
    @Builder.Default
    private boolean active = true;

    @Field("sort_order")
    private Integer sortOrder;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;
}
