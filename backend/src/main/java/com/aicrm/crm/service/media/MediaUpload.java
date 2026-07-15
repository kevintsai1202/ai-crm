package com.aicrm.crm.service.media;

/** 已完成驗證、準備寫入 object storage 的媒體內容。 */
public record MediaUpload(String originalFilename, String contentType, byte[] bytes) {}
