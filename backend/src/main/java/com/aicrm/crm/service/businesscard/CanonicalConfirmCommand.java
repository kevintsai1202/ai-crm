package com.aicrm.crm.service.businesscard;

import com.aicrm.crm.api.Dtos.ConfirmBusinessCardRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;

/** 名片確認唯一 canonical command，雜湊與 CRM 寫入共用同一份資料。 */
public record CanonicalConfirmCommand(String customerAction,Long customerId,String customerName,String customerEmail,
        String customerPhone,String taxId,String industry,String contactName,String contactTitle,String contactEmail,
        String opportunityName,BigDecimal opportunityAmount,LocalDate expectedCloseDate,LocalDateTime callAt) {

    /** 正規化語意等價輸入並在雜湊前驗證必要欄位。 */
    public static CanonicalConfirmCommand from(ConfirmBusinessCardRequest r){
        if(r==null)throw new IllegalArgumentException("確認內容不可為空");
        String action=required(r.customerAction(),"customerAction").toUpperCase(Locale.ROOT);
        if(!action.equals("CREATE")&&!action.equals("MERGE"))throw new IllegalArgumentException("customerAction 必須為 CREATE 或 MERGE");
        if(action.equals("MERGE")&&r.customerId()==null)throw new IllegalArgumentException("MERGE 必須提供 customerId");
        BigDecimal amount=r.opportunityAmount()==null?BigDecimal.ZERO:r.opportunityAmount();if(amount.signum()<0)throw new IllegalArgumentException("opportunityAmount 不可為負數");
        amount=amount.signum()==0?BigDecimal.ZERO:amount.stripTrailingZeros();
        String customerEmail=email(required(r.customerEmail(),"customerEmail")),contactEmail=email(required(r.contactEmail(),"contactEmail"));
        return new CanonicalConfirmCommand(action,r.customerId(),required(r.customerName(),"customerName"),customerEmail,
                phone(required(r.customerPhone(),"customerPhone")),required(r.taxId(),"taxId"),required(r.industry(),"industry"),
                required(r.contactName(),"contactName"),required(r.contactTitle(),"contactTitle"),contactEmail,
                required(r.opportunityName(),"opportunityName"),amount,r.expectedCloseDate(),
                java.util.Objects.requireNonNull(r.callAt(),"callAt"));
    }
    private static String required(String value,String field){String v=value==null?"":value.trim();if(v.isBlank())throw new IllegalArgumentException(field+" 為必填");return v;}
    private static String email(String value){String v=value.toLowerCase(Locale.ROOT);if(!v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("email 格式錯誤");return v;}
    private static String phone(String value){String v=value.replaceAll("\\D","");if(v.startsWith("886"))v="0"+v.substring(3);if(v.length()<6||v.length()>20)throw new IllegalArgumentException("phone 格式錯誤");return v;}
}
