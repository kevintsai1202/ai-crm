package com.aicrm.crm.service.vision;

/** 已驗證用途能力的模型與 Provider 連線資訊。 */
public record AiModelAssignment(String model, Long providerId, String baseUrl, String apiKey) {}
