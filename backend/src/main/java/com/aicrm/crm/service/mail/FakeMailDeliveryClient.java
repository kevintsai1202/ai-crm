package com.aicrm.crm.service.mail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 供 E2E／本機驗收使用的 deterministic 郵件寄送 fake。
 * 僅在設定 {@code app.mail.fake.enabled=true} 時啟用，並以 {@link Primary} 覆蓋正式
 * {@link ZeaburSendmailClient}，讓 live 後端無需真實 Sendmail 憑證即可跑完整寄送流程。
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.mail.fake.enabled", havingValue = "true")
public class FakeMailDeliveryClient implements MailDeliveryClient {

    /** 以收件者＋主旨的雜湊產生可重現 message id，不做任何實際寄送。 */
    @Override
    public DeliveryResult send(ApprovedEmail email) {
        String seed = (email.recipient() == null ? "" : email.recipient()) + "|"
                + (email.subject() == null ? "" : email.subject());
        return new DeliveryResult("fake-" + shortHash(seed));
    }

    /** 產生 seed 的短雜湊，作為 deterministic message id 尾碼。 */
    private String shortHash(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("無法產生 fake message id", e);
        }
    }
}
