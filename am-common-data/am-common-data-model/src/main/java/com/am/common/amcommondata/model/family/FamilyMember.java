package com.am.common.amcommondata.model.family;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Soft family persona under a single auth owner (not a separate Keycloak user). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class FamilyMember {
    private String id;
    private String ownerUserId;
    private String displayName;
    private String email;
    /** CAS | NPS | MANUAL */
    private String source;
    private LocalDateTime createdAt;
    /** Populated on list/get when counted against owner portfolios. */
    private Integer portfolioCount;
}
