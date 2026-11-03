package com.am.common.amcommondata.mapper.asset;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.model.asset.pension.NPSModel;

/**
 * Maps portfolio {@code npsModels} rows — preserves pran/tier on {@link NPSModel}.
 */
@Component
public class NpsAssetMapper extends AssetMapper<NPSModel, AssetDocument> {

    @Override
    protected NPSModel createModel() {
        return NPSModel.builder().build();
    }

    @Override
    protected AssetDocument createDocument() {
        return AssetDocument.builder().build();
    }
}
