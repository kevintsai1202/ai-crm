package com.aicrm.crm.service.media;

/** 僅供即時用途測試使用的已驗證媒體內容，不會寫入 object storage。 */
public record ValidatedMedia(byte[] bytes, String mimeType) {
    /** 防止呼叫端持有並修改服務內部的媒體陣列。 */
    public ValidatedMedia {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /** 每次存取都回傳副本，避免驗證後內容被替換。 */
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
