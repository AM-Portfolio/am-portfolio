package com.am.common.amcommondata.model.asset.cryptocurrency;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class CryptocurrencyModel extends AssetModel {
    private String blockchain;
    private String tokenType; // Native, ERC20, BEP20, etc.
    private String contractAddress;
    private Long decimals;
    private Double circulatingSupply;
    private Double totalSupply;
    private Double maxSupply;
    private String consensus; // PoW, PoS, etc.
    private String walletAddress;
    private String network; // Mainnet, Testnet
    private String storageType; // Hot Wallet, Cold Storage
    private Double gasPrice;
    private String transactionHash;
    private Double networkFee;
    private String exchangePlatform;
}
