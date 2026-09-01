package com.am.common.amcommondata.document.portfolio;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.asset.equity.EquityDocument;
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
// Unique one BROKER portfolio per owner+brokerType. Baskets are excluded via partial filter.
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "owner_broker_broker_only_idx",
        def = "{'owner': 1, 'brokerType': 1}",
        unique = true,
        partialFilter = "{'$or':[{'portfolioKind':'BROKER'},{'portfolioKind':{'$exists':false}},{'portfolioKind':null}]}")
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
    
    @Field("totalValue")
    private Double totalValue;
    
    @Field("brokerType")
    private BrokerType brokerType;

    /** BROKER (default/null) or BASKET carve-out. */
    @Field("portfolioKind")
    private PortfolioKind portfolioKind;

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