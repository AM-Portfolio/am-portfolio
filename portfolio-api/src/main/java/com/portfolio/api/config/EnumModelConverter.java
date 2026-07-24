package com.portfolio.api.config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JavaType;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@Component
public class EnumModelConverter implements ModelConverter {
    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (type.isSchemaProperty()) {
            JavaType javaType = Json.mapper().constructType(type.getType());
            if (javaType != null) {
                Class<?> cls = javaType.getRawClass();
                if (cls != null && cls.isEnum() && cls.getPackage() != null
                        && cls.getPackage().getName().startsWith("com.am.common.amcommondata.model.enums")) {
                    StringSchema schema = new StringSchema();
                    schema.setEnum(Arrays.stream(cls.getEnumConstants())
                        .map(c -> ((Enum<?>) c).name())
                        .collect(Collectors.toList()));
                    return schema;
                }
            }
        }
        return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
    }
}
