package com.guiderun.server.config

import com.guiderun.server.interceptor.ProvisioningCheckInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring MVC 全局拦截器装配。
 * 注册 [ProvisioningCheckInterceptor] 拦截未完成角色选择的用户，阻止其访问业务接口。
 */
@Configuration
class WebMvcConfig(
    private val provisioningCheckInterceptor: ProvisioningCheckInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(provisioningCheckInterceptor)
    }
}
