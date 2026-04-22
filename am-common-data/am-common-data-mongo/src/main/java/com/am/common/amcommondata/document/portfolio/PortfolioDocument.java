package com.am.common.amcommondata.document.portfolio;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.asset.equity.EquityDocument;
import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.Currency;
import com.am.common.amcommondata.model.enums.DocumentStatus;
import com.am.common.amcommondata.model.enums.FundType;
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