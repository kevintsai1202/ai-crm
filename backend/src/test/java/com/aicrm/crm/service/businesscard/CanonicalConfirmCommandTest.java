package com.aicrm.crm.service.businesscard;

import static org.assertj.core.api.Assertions.*;
import com.aicrm.crm.api.Dtos;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

/** Action-aware canonical command 單元測試。 */
class CanonicalConfirmCommandTest {
    /** CREATE 強制忽略 customerId，並保留會建立新客戶的欄位。 */
    @Test void createIgnoresCustomerId(){var command=CanonicalConfirmCommand.from(request("CREATE",99L,"客戶","a@example.com","0912345678"));assertThat(command.customerId()).isNull();assertThat(command.customerName()).isEqualTo("客戶");}
    /** MERGE 只保留 customerId，ignored new-customer 欄位不驗證也不進 hash。 */
    @Test void mergeIgnoresNewCustomerFields(){var command=CanonicalConfirmCommand.from(request("MERGE",7L,null,"bad",null));assertThat(command.customerId()).isEqualTo(7L);assertThat(command.customerName()).isNull();assertThat(command.customerEmail()).isNull();assertThat(command.customerPhone()).isNull();}
    /** 真正共用寫入欄位仍會正規化並保留。 */
    @Test void commonFieldsRemainCanonical(){var command=CanonicalConfirmCommand.from(request(" merge ",7L,"ignored","bad","x"));assertThat(command.contactEmail()).isEqualTo("buyer@example.com");assertThat(command.opportunityAmount()).isEqualByComparingTo("1000");}
    private Dtos.ConfirmBusinessCardRequest request(String action,Long id,String name,String email,String phone){return new Dtos.ConfirmBusinessCardRequest(action,id,name,email,phone,"tax","industry"," 王小明 "," 採購 "," BUYER@EXAMPLE.COM "," 商機 ",new BigDecimal("1000.00"),LocalDate.of(2026,12,1),LocalDateTime.of(2026,8,1,10,0));}
}
