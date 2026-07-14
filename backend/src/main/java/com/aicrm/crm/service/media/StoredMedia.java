package com.aicrm.crm.service.media;

/** Object storage 寫入結果。 */
public record StoredMedia(String objectKey, long sizeBytes, String sha256) {}
