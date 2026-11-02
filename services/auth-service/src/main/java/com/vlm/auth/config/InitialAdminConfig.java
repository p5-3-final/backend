package com.vlm.auth.config;

import com.vlm.auth.application.InitialAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InitialAdminProperties.class)
class InitialAdminConfig {}
