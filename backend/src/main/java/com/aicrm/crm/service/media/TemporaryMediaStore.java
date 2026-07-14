package com.aicrm.crm.service.media;

import java.io.InputStream;

/** 暫存媒體 object storage 邊界。 */
public interface TemporaryMediaStore {
    /** 寫入已驗證媒體並回傳不可預測 object key。 */
    StoredMedia put(MediaUpload upload);
    /** 開啟媒體串流；呼叫端必須關閉回傳串流。 */
    InputStream get(String objectKey);
    /** 冪等刪除媒體；不存在視為成功。 */
    void delete(String objectKey);
}
