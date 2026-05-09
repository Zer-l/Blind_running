package com.guiderun.server.config

import com.guiderun.server.interceptor.ProvisioningCheckInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val provisioningCheckInterceptor: ProvisioningCheckInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(provisioningCheckInterceptor)
    }
}
