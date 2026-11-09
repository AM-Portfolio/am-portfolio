package com.am.common.amcommondata.mapper.asset;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.model.asset.AssetModel;

/**
 * Mapper for Option A non-equity asset lists (MF, bonds, commodities, cash).
 */
@Component
public class GenericAssetMapper extends AssetMapper<AssetModel, AssetDocument> {

    @Override
    protected AssetModel createModel() {
        return AssetModel.builder().build();
    }

    @Override
    protected AssetDocument createDocument() {
        return AssetDocument.builder().build();
    }
}
