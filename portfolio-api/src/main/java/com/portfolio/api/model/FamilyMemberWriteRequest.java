package com.portfolio.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@Schema(description = "Create or update a soft family member under the authenticated owner")
public class FamilyMemberWriteRequest {
    @Schema(description = "Display name", example = "Priyanshu Kumar")
    private String displayName;

    @Schema(description = "Optional email used for CAS/NPS identity match", example = "priyanshukpd@gmail.com")
    private String email;
}
