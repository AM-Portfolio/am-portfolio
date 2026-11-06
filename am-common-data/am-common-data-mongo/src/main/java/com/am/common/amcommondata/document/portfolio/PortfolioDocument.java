package com.am.common.amcommondata.document.portfolio;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.document.asset.equity.EquityDocument;
import com.am.common.amcommondata.document.basket.BasketPortfolioDocument;
import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.Currency;
import com.am.common.amcommondata.model.enums.DocumentStatus;
import com.am.common.amcommondata.model.enums.FundType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.model.enums.PortfolioStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "portfolios")
// First-class brokers: one BROKER book per owner+brokerType+family (excludes CAS_DEMAT).
// Mongo partial indexes cannot use $exists:false / $nin (expands to $not) — use $in of first-class types.
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "owner_broker_family_broker_only_idx",
        def = "{'owner': 1, 'brokerType': 1, 'familyMemberId': 1}",
        unique = true,
        partialFilter = "{'portfolioKind':'BROKER','brokerType':{'$in':["
                + "'DHAN','ZERODHA','MSTOCK','GROWW','GROW','KOTAK','ANGEL_ONE','UPSTOX','FYERS','HDFC','CDSL','NPS'"
                + "]}}")
// CAS_DEMAT: one book per demat BOID (or mf:cas sentinel).
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "owner_cas_demat_boid_family_idx",
        def = "{'owner': 1, 'brokerType': 1, 'dematBoid': 1, 'familyMemberId': 1}",
        unique = true,
        partialFilter = "{'portfolioKind':'BROKER','brokerType':'CAS_DEMAT'}")
// At most one active DEMO sandbox per owner.
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "owner_demo_unique_idx",
        def = "{'owner': 1}",
        unique = true,
        partialFilter = "{'portfolioKind':'DEMO'}")
public class PortfolioDocument extends BaseDocument {
    @Field("name")
    private String name;
    
    @Field("description")
    private String description;
    
    @Field("owner")
    private String owner;
    
    @Field("currency")
    private String currency; // Store as String in MongoDB
    
    @Field("fundType")
    private FundType fundType;
    
    @Field("portfolioStatus")
    private PortfolioStatus portfolioStatus; // Store as String in MongoDB
    
    @Field("tags")
    private List<String> tags;
    
    @Field("notes")
    private String notes;
    
    @Field("equities")
    private List<EquityDocument> equities;

    /** Option A multi-asset lists (default empty / absent). */
    @Field("mutualFunds")
    private List<AssetDocument> mutualFunds;

    @Field("bonds")
    private List<AssetDocument> bonds;

    @Field("commodities")
    private List<AssetDocument> commodities;

    @Field("cash")
    private List<AssetDocument> cash;

    @Field("npsModels")
    private List<AssetDocument> npsModels;
    
    @Field("totalValue")
    private Double totalValue;
    
    @Field("brokerType")
    private BrokerType brokerType;

    /** BROKER (default/null), BASKET, DEMO, PAPER, or DELETED. */
    @Field("portfolioKind")
    private PortfolioKind portfolioKind;

    /** PR56 nested basket metadata; legacy flat fields below remain for dual-read. */
    @Field("basket")
    private BasketPortfolioDocument basket;

    @Field("sourcePortfolioId")
    private String sourcePortfolioId;

    @Field("etfIsin")
    private String etfIsin;

    @Field("etfName")
    private String etfName;

    @Field("createdFromBasketAt")
    private java.time.LocalDateTime createdFromBasketAt;

    @Field("gapMissingCount")
    private Integer gapMissingCount;

    @Field("investmentAmount")
    private Double investmentAmount;

    @Field("replicaScore")
    private Double replicaScore;

    @Field("coverageAfterCreation")
    private Double coverageAfterCreation;

    /** Quantity reserved from this BROKER book into baskets. */
    @Field("allocations")
    private List<HoldingAllocationDocument> allocations;

    @Field("lastLoginDate")
    private LocalDate lastLoginDate; // Updated on each user login — used for active-user filtering

    /** Soft family persona under owner; null = primary. */
    @Field("familyMemberId")
    private String familyMemberId;

    @Field("familyMemberName")
    private String familyMemberName;

    /** BOID for CAS_DEMAT books; null/absent for first-class brokers. */
    @Field("dematBoid")
    private String dematBoid;
    
    // Helper methods for enum conversions
    public void setCurrency(Currency currency) {
        this.currency = currency != null ? currency.name() : null;
    }
    
    public Currency getCurrency() {
        return this.currency != null ? Currency.valueOf(this.currency) : null;
    }
    
    public void setPortfolioStatus(PortfolioStatus status) {
        this.portfolioStatus = status != null ? status : null;
    }
    
    public PortfolioStatus getPortfolioStatus() {
        return this.portfolioStatus != null ? this.portfolioStatus : null;
    }
    
    // Override base status methods to use DocumentStatus enum
    @Override
    public void setStatus(DocumentStatus status) {
        super.setStatus(status);
    }
    
    @Override
    public DocumentStatus getStatus() {
        return super.getStatus();
    }
}
