package com.sk.bds.ticket.api;

import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.PropertyReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Locale;

@SpringBootApplication(
        scanBasePackages = {
                "com.sk.bds.ticket.api.config",
                "com.sk.bds.ticket.api.controller",
                "com.sk.bds.ticket.api.exception",
                "com.sk.bds.ticket.api.interceptor",
                "com.sk.bds.ticket.api.service",
                "com.sk.bds.ticket.api.data.model"
        }
)
@EnableTransactionManagement
@EnableScheduling
@Slf4j
public class Application {
    private static ConfigurableApplicationContext appContext;

    @Autowired
    PropertyReader property;

    public Application() {
    }

    @PostConstruct
    private void initApplication() {
        AppConfig.initialize(property);
    }

    public static void main(String[] args) {
        System.out.println("@@@ Application main() enter @@@");
        Locale.setDefault(Locale.US);
        appContext = SpringApplication.run(Application.class, args);
    }

    //////////////////////////////////////////////////////////////////////////
    public static void shutdown() {
        log.warn("Application.shutdown");
        if (appContext != null) {
            Thread thread = new Thread(() -> {
                try {
                    log.info("Application.shutdown() - waiting 2 secs.");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("Application.shutdown() - close running context. And System.exit(1)");
                appContext.close();
                System.exit(1);
            });
            thread.setDaemon(false);
            thread.start();
        } else {
            log.info("Application.shutdown() - System.exit(1)");
            System.exit(1);
        }
    }

    public static void restart() {
        log.warn("Application.restart");
        //Reference : https://www.baeldung.com/java-restart-spring-boot-app
        ApplicationArguments args = appContext.getBean(ApplicationArguments.class);
        Thread thread = new Thread(() -> {
            log.info("Application.restart() - close previously running context.");
            try {
                log.info("Application.shutdown() - waiting 2 secs before closing.");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            appContext.close();
            try {
                log.info("Application.shutdown() - waiting 2 secs for starting.");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            appContext = SpringApplication.run(Application.class, args.getSourceArgs());
            log.info("Application.restart() new context : {}", appContext);
        });
        thread.setDaemon(false);
        thread.start();
    }
}
