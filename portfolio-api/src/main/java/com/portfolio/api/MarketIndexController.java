package com.portfolio.api;

import com.portfolio.model.market.IndexIndices;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.MarketIndexIndicesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market-index")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Market Indices", description = "Endpoints for retrieving market index data")
public class MarketIndexController {
    
    private final MarketIndexIndicesService marketIndexService;
     
    @Operation(summary = "Get all market indices", description = "Retrieves all market indices with optional filtering by interval and type")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Market indices retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = IndexIndices.class))),
        @ApiResponse(responseCode = "400", description = "Invalid interval parameter"),
        @ApiResponse(responseCode = "404", description = "No market indices found")
    })
    @GetMapping("/all")
    public ResponseEntity<List<IndexIndices>> getAllMarketIndices(
        @RequestParam(required = false) String interval,
        @RequestParam(required = false) String type) {
        log.info("MarketIndexController - getAllMarketIndices called with interval: {} and type: {}", interval, type);
        
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            List<IndexIndices> marketIndices = marketIndexService.getAllMarketIndices(timeInterval, type);
            String indicesInfo = marketIndices != null 
                ? marketIndices.stream()
                    .map(IndexIndices::getIndexSymbol)
                    .collect(Collectors.joining(", "))
                : "none";
            log.info("MarketIndexController - getAllMarketIndices - Found {} market indices: {}", 
                marketIndices != null ? marketIndices.size() : 0, indicesInfo);
            return ResponseEntity.ok(marketIndices);
        } catch (IllegalArgumentException e) {
            log.error("MarketIndexController - getAllMarketIndices - Invalid market index ID: {}", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
