package com.portfolio.api.model;

import java.util.List;

import com.am.common.amcommondata.model.asset.AssetModel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Replace one Option A asset-class list (bonds, commodities, or cash)")
public class AssetClassReplaceRequest {
    @Schema(description = "Holdings for this class; empty list clears the class")
    private List<AssetModel> items;
}
