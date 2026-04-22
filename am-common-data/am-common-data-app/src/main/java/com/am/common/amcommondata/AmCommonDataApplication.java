package com.am.common.amcommondata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ComponentScans({
    @ComponentScan("com.am.common.amcommondata"),
    @ComponentScan("com.am.common.amcommondata.service"),
    @ComponentScan("com.portfolio")
})
@EnableJpaRepositories(basePackages = {
    "com.am.common.amcommondata.repository.asset",
    "com.am.common.amcommondata.repository.security"
})
@EntityScan(basePackages = {
    "com.am.common.amcommondata.domain",
    "com.am.common.amcommondata.domain.asset"
})
@SpringBootApplication
public class AmCommonDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(AmCommonDataApplication.class, args);
    }
}
