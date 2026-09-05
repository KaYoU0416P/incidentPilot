package com.incidentpilot.common.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AsyncConfiguration {
    @Bean(destroyMethod = "close")
    ExecutorService applicationTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
