package com.aicrm.crm.service.vision;

/** Vision 外部服務 timeout、HTTP 或格式錯誤。 */
public class VisionServiceException extends RuntimeException {
    public VisionServiceException(String message) { super(message); }
    public VisionServiceException(String message, Throwable cause) { super(message, cause); }
}
