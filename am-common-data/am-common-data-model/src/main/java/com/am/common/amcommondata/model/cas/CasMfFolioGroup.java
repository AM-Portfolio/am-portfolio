package com.am.common.amcommondata.model.cas;

import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CasMfFolioGroup {
    private String folio;
    private String amcName;
    private String rtaName;
    private String advisorName;
    private String ucc;
    private List<MutualFundModel> holdings;
}
