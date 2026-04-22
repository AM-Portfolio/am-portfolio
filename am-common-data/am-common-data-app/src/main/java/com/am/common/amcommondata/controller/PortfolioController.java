// package com.am.common.amcommondata.controller;

// import com.am.common.amcommondata.model.PortfolioModel;
// import com.am.common.amcommondata.service.PortfolioService;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.List;
// import java.util.UUID;

// @RestController
// @RequestMapping("/portfolios")
// @RequiredArgsConstructor
// public class PortfolioController {
//     private final PortfolioService portfolioService;

//     @GetMapping
//     public ResponseEntity<List<PortfolioModel>> getAllPortfolios() {
//         return ResponseEntity.ok(portfolioService.getAllPortfolios());
//     }

//     @GetMapping("/{id}")
//     public ResponseEntity<PortfolioModel> getPortfolioById(@PathVariable UUID id) {
//         return ResponseEntity.ok(portfolioService.getPortfolio(id));
//     }

//     @PostMapping
//     public ResponseEntity<PortfolioModel> createPortfolio(@RequestBody PortfolioModel portfolio) {
//         return ResponseEntity.ok(portfolioService.createPortfolio(portfolio));
//     }

//     @PutMapping("/{id}")
//     public ResponseEntity<PortfolioModel> updatePortfolio(@PathVariable UUID id, @RequestBody PortfolioModel portfolio) {
//         return ResponseEntity.ok(portfolioService.updatePortfolio(id, portfolio));
//     }

//     @DeleteMapping("/{id}")
//     public ResponseEntity<Void> deletePortfolio(@PathVariable UUID id) {
//         portfolioService.deletePortfolio(id);
//         return ResponseEntity.noContent().build();
//     }
// }
