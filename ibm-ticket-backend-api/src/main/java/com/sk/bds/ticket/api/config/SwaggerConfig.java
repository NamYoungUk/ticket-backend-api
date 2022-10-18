package com.sk.bds.ticket.api.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.DocExpansion;
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class})
public class SwaggerConfig {
    @Bean
    public Docket ticketApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .groupName("ticket")
                .apiInfo(ticketApiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sk.bds.ticket.api.controller"))
                .paths(PathSelectors.ant("/ibm/**"))
                .build();
    }

    @Bean
    public Docket cloudzApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .groupName("cloudz")
                .apiInfo(userApiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sk.bds.ticket.api.controller"))
                .paths(PathSelectors.ant("/cloudz/**"))
                //.paths(PathSelectors.ant("/**"))
                .build();
    }

    @Bean
    public Docket slaApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .groupName("sla")
                .apiInfo(slaApiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sk.bds.ticket.api.controller"))
                .paths(PathSelectors.ant("/sla/**"))
                //.paths(PathSelectors.ant("/**"))
                .build();
    }

    @Bean
    public Docket agentApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .groupName("agent")
                .apiInfo(agentApiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sk.bds.ticket.api.controller"))
                .paths(PathSelectors.ant("/agent/**"))
                //.paths(PathSelectors.ant("/**"))
                .build();
    }

    private ApiInfo ticketApiInfo() {
        return new ApiInfoBuilder()
                .title("IBM Ticket API")
                .description("헬프데스크를 통한 IBM 티켓 생성/업데이트 규칙에 의해 호출되는 API 규격 정의 ")
                .version("1.0.0")
                .build();
    }

    private ApiInfo slaApiInfo() {
        return new ApiInfoBuilder()
                .title("SLA 관리용 API")
                .description("SLA 티켓에 대한 조회 API 규격 정의")
                .version("1.0.0")
                .build();
    }

    private ApiInfo agentApiInfo() {
        return new ApiInfoBuilder()
                .title("Agent 관리용 API")
                .description("Agent에 대한 조회/추가/수정/삭제 API 규격 정의")
                .version("1.0.0")
                .build();
    }

    private ApiInfo userApiInfo() {
        return new ApiInfoBuilder()
                .title("Cloud Z Account 조회 API")
                .description("헬프데스크를 통한 IBM 티켓 생성/업데이트 규칙에 의해 호출되는 CSP API 사용을 위한 User Account 조회 API 정의 ")
                .version("1.0.0")
                .build();
    }

    @Bean
    UiConfiguration uiConfig() {
        return UiConfigurationBuilder.builder()
                .docExpansion(DocExpansion.LIST)
                .build();
    }
}
