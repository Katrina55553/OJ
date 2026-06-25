package com.oj.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Knife4j 接口文档配置（OpenAPI3 风格，适配 Spring Boot 3）
 * https://doc.xiaominfo.com/knife4j/documentation/get_start.html
 */
@Configuration
@Profile({"dev", "test"})
public class Knife4jConfig {

    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder()
                .group("default")
                // 指定 Controller 扫描包路径
                .packagesToScan("com.oj.controller")
                .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("接口文档")
                        .description("oj-backend")
                        .version("1.0"));
    }
}
