package com.vlm.auth.account.infra.config;

import com.vlm.auth.account.application.InitialAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InitialAdminProperties.class)
class InitialAdminConfig {}
