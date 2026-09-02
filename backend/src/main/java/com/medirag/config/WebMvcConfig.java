package com.medirag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Value("${frontend.dist-path:medirag-frontend/dist}")
    private String frontendDistPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = "file:" + Paths.get(uploadPath).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);

        String frontendRootLocation = "file:" + Paths.get(frontendDistPath).toAbsolutePath() + "/";
        String frontendAssetsLocation = "file:" + Paths.get(frontendDistPath, "assets").toAbsolutePath() + "/";

        registry.addResourceHandler("/", "/index.html", "/favicon.ico")
                .addResourceLocations(frontendRootLocation, "classpath:/static/");

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(frontendAssetsLocation, "classpath:/static/assets/");
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }
}
