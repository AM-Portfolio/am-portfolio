package com.portfolio.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
public class SnapshotTriggerTest {

    @Autowired
    private PortfolioHistoryScheduler scheduler;

    @Test
    public void triggerJob() {
        System.out.println("TRIGGERING END OF DAY JOB MANUALLY FOR VERIFICATION...");
        scheduler.runEndOfDayJob();
        System.out.println("JOB COMPLETED.");
    }
}
