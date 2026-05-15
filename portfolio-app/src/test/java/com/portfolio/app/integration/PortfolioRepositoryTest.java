package com.portfolio.app.integration;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB integration test using Testcontainers.
 * Verifies that PortfolioDocumentRepository correctly persists and retrieves data.
 */
class PortfolioRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private PortfolioDocumentRepository portfolioDocumentRepository;

    @Test
    void testSaveAndFindByOwner() {
        // Prepare test data
        String ownerId = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
        PortfolioDocument doc = new PortfolioDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setOwner(ownerId);
        doc.setName("Integration Test Portfolio");

        // Persist
        portfolioDocumentRepository.save(doc);

        // Retrieve
        List<PortfolioDocument> portfolios = portfolioDocumentRepository.findByOwner(ownerId);
        
        // Verify
        assertFalse(portfolios.isEmpty(), "Should find at least one portfolio for the owner");
        assertEquals("Integration Test Portfolio", portfolios.get(0).getName(), "Portfolio name mismatch");
        assertEquals(ownerId, portfolios.get(0).getOwner(), "Owner ID mismatch");
    }

    @Test
    void testFindAllDistinctOwners() {
        // Prepare data with multiple owners
        String owner1 = "owner1";
        String owner2 = "owner2";
        
        PortfolioDocument p1 = new PortfolioDocument();
        p1.setId(UUID.randomUUID().toString());
        p1.setOwner(owner1);
        p1.setName("P1");
        
        PortfolioDocument p2 = new PortfolioDocument();
        p2.setId(UUID.randomUUID().toString());
        p2.setOwner(owner2);
        p2.setName("P2");

        portfolioDocumentRepository.saveAll(List.of(p1, p2));

        // Use aggregation query
        List<String> distinctOwners = portfolioDocumentRepository.findAllDistinctOwners();

        // Verify
        assertTrue(distinctOwners.contains(owner1));
        assertTrue(distinctOwners.contains(owner2));
    }
}
