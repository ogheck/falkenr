package com.devtools.ui.autoconfigure;

import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

class DevToolsUiWebMvcConfigurer implements WebMvcConfigurer {

    private final DevToolsRequestInterceptor requestInterceptor;

    DevToolsUiWebMvcConfigurer(DevToolsRequestInterceptor requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(DevToolsUiConstants.ROOT_PATH, DevToolsUiConstants.ROOT_PATH + "/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(DevToolsUiConstants.ASSETS_PATH)
                .addResourceLocations(DevToolsUiConstants.STATIC_RESOURCE_LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler(DevToolsUiConstants.ROOT_PATH + "/**")
                .addResourceLocations(DevToolsUiConstants.STATIC_RESOURCE_LOCATION)
                .setCacheControl(CacheControl.noStore());
    }
}
