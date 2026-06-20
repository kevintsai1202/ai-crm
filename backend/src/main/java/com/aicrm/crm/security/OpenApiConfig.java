package com.aicrm.crm.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 組態設定類別。
 * 註冊全域 SecurityScheme，為 API 文件新增 Bearer JWT Token 鎖頭與認證功能。
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "AI CRM API 系統", version = "v1.0", description = "AI 賦能 CRM 系統 RESTful API"),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "請輸入您的 JWT 認證 Token 格式: Bearer <token>"
)
public class OpenApiConfig {
    // 此類別主要做為 Swagger/OpenAPI 的宣告，無須包含實體邏輯
}
