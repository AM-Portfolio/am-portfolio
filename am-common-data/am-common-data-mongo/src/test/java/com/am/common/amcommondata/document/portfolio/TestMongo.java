package com.am.common.amcommondata.document.portfolio;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.MongoClients;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import java.util.List;
import org.junit.Test;

public class TestMongo {
    @Test
    public void testMongo() {
        try {
            SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(MongoClients.create("mongodb://admin:password123@localhost:27017/portfolio?authSource=admin"), "portfolio");
            MongoTemplate template = new MongoTemplate(factory);
            List<PortfolioDocument> docs = template.find(new Query(Criteria.where("owner").is("af30e0e6-6a98-40ba-875e-a1490c0c8a11")), PortfolioDocument.class);
            System.out.println("Success: " + docs.size());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
