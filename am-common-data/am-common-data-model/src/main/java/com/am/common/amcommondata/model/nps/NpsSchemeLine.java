package com.am.common.amcommondata.model.nps;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class NpsSchemeLine {
    private String isin;
    private String schemeCode;
    private String name;
    private String tier;
    private String schemeClass;
    private String schemeOption;
    private Double units;
    private Double statementNav;
    private Double liveNav;
    private Double statementValue;
    private Double liveValue;
    private Double gainVsStatement;
    private String isinResolveStatus;
    private String pran;
}
