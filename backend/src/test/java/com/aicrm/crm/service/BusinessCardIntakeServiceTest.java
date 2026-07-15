package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.businesscard.BusinessCardConflictException;
import com.aicrm.crm.service.businesscard.CanonicalConfirmCommand;
import com.aicrm.crm.service.media.TemporaryMediaService;
import com.aicrm.crm.service.vision.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/** 名片辨識重複比對、原子確認與冪等規則測試。 */
class BusinessCardIntakeServiceTest {
    private final BusinessCardIntakeRepository intakes=mock(BusinessCardIntakeRepository.class);
    private final TemporaryMediaService media=mock(TemporaryMediaService.class);
    private final BusinessCardRecognitionClient vision=mock(BusinessCardRecognitionClient.class);
    private final SystemSettingService settings=mock(SystemSettingService.class);
    private final CustomerRepository customers=mock(CustomerRepository.class);
    private final ContactRepository contacts=mock(ContactRepository.class);
    private final OpportunityRepository opportunities=mock(OpportunityRepository.class);
    private final CrmTaskRepository tasks=mock(CrmTaskRepository.class);
    private final AppUserRepository users=mock(AppUserRepository.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final EntityManager entityManager=mock(EntityManager.class);
    private final Query advisoryQuery=mock(Query.class);
    private final JwtService.AuthPrincipal principal=new JwtService.AuthPrincipal("sales@example.com","業務",Role.SALES);
    private BusinessCardIntakeService service;

    /** 每案建立乾淨 service。 */
    @BeforeEach void setUp(){when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryQuery);when(advisoryQuery.setParameter(anyInt(),any())).thenReturn(advisoryQuery);when(advisoryQuery.getSingleResult()).thenReturn(1L);service=new BusinessCardIntakeService(intakes,media,vision,settings,customers,contacts,opportunities,tasks,users,mapper,entityManager);}

    /** Email exact、正規化電話與公司模糊名稱均只產生候選，不自動合併。 */
    @Test void create_reportsAllDuplicateReasonsWithoutWritingCrm(){
        var assignment=new AiModelAssignment("vision",1L,"http://provider","secret");
        TemporaryMedia staged=mock(TemporaryMedia.class); when(staged.getMimeType()).thenReturn("image/png"); when(staged.getId()).thenReturn(8L);
        when(settings.resolveOcrAssignment()).thenReturn(assignment); when(media.stage(any(),eq(MediaPurpose.BUSINESS_CARD),eq(principal))).thenReturn(staged);
        when(media.read(staged)).thenReturn(new byte[]{1});
        when(vision.recognize(any(),eq("image/png"),eq(assignment))).thenReturn(new Dtos.RecognizedBusinessCard(
                "王小明","採購","BUYER@EXAMPLE.COM","+886 912-345-678","台灣未來科技股份有限公司",null,Map.of(),List.of()));
        AppUser owner=mock(AppUser.class); when(owner.getUsername()).thenReturn(principal.username());
        Customer existing=mock(Customer.class); when(existing.getId()).thenReturn(3L); when(existing.getName()).thenReturn("台灣未來科技");
        when(existing.getEmail()).thenReturn("buyer@example.com"); when(existing.getPhone()).thenReturn("0912345678"); when(existing.getOwner()).thenReturn(owner); when(existing.getContacts()).thenReturn(List.of());
        when(customers.findAll()).thenReturn(List.of(existing)); when(intakes.save(any())).thenAnswer(i->i.getArgument(0));

        var result=service.create(mock(org.springframework.web.multipart.MultipartFile.class),principal);

        assertThat(result.duplicateCandidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.matchedBy()).contains("EMAIL_EXACT","PHONE_NORMALIZED","COMPANY_FUZZY"));
        verifyNoInteractions(contacts,opportunities,tasks);
    }

    /** 明確 MERGE 會一次建立 Contact、Opportunity 與 PHONE_CALL task。 */
    @Test void confirm_mergeExisting_writesAllRelationsOnce(){
        TransactionSynchronizationManager.initSynchronization();
        try {
            BusinessCardIntake intake=mock(BusinessCardIntake.class); TemporaryMedia staged=mock(TemporaryMedia.class);
            when(intake.getId()).thenReturn(7L); when(intake.getCreatorUsername()).thenReturn(principal.username()); when(intake.getStatus()).thenReturn(BusinessCardStatus.REVIEW_PENDING); when(intake.getMedia()).thenReturn(staged);
            when(intakes.findByIdForUpdate(7L)).thenReturn(Optional.of(intake));
            AppUser owner=mock(AppUser.class); when(users.findByUsername(principal.username())).thenReturn(Optional.of(owner));
            Customer customer=mock(Customer.class); when(customer.getId()).thenReturn(4L); when(customer.getOwner()).thenReturn(owner); when(owner.getUsername()).thenReturn(principal.username()); when(customers.findById(4L)).thenReturn(Optional.of(customer));
            Contact contact=mock(Contact.class); when(contact.getId()).thenReturn(11L); when(contact.getName()).thenReturn("王小明"); when(contacts.save(any())).thenReturn(contact);
            when(opportunities.save(any())).thenAnswer(i->{Opportunity o=i.getArgument(0); setId(o,12L); return o;});
            when(tasks.save(any())).thenAnswer(i->{CrmTask t=i.getArgument(0); setId(t,13L); return t;});

            service.confirm(7L,request("MERGE",4L),principal,"key-1");

            verify(contacts,times(1)).save(any(Contact.class)); verify(opportunities,times(1)).save(any(Opportunity.class)); verify(tasks,times(1)).save(argThat(t->t.getType()==CrmTaskType.PHONE_CALL&&t.getSource()==CrmTaskSource.BUSINESS_CARD));
            verify(intake).confirm(eq(principal.username()),eq("key-1"),anyString(),eq(4L),eq(11L),eq(12L),eq(13L));
            verify(media,never()).deleteConfirmed(any());
            var synchronization=TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(media,never()).deleteConfirmed(any());
            synchronization.afterCommit();
            verify(media).deleteConfirmed(staged);
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }

    /** 同 key 同 payload replay 原結果；同 key 不同 payload 必須 409 且零寫入。 */
    @Test void confirm_idempotencyReplayAndConflict(){
        BusinessCardIntake intake=mock(BusinessCardIntake.class); when(intake.getId()).thenReturn(7L); when(intake.getCreatorUsername()).thenReturn(principal.username()); when(intake.getStatus()).thenReturn(BusinessCardStatus.CONFIRMED);
        var request=request("CREATE",null); when(intake.getIdempotencyKey()).thenReturn("key-1"); when(intake.getIdempotencyPayloadHash()).thenReturn(hash(write(CanonicalConfirmCommand.from(request))));
        when(intake.getCustomerId()).thenReturn(1L); when(intake.getContactId()).thenReturn(2L); when(intake.getOpportunityId()).thenReturn(3L); when(intake.getTaskId()).thenReturn(4L); when(intakes.findByIdForUpdate(7L)).thenReturn(Optional.of(intake));
        assertThat(service.confirm(7L,request,principal,"key-1").taskId()).isEqualTo(4L);
        assertThatThrownBy(()->service.confirm(7L,request("CREATE",null,"另一商機"),principal,"key-1")).isInstanceOf(BusinessCardConflictException.class);
        verifyNoInteractions(contacts,opportunities,tasks);
    }

    /** 產生有效確認請求。 */
    private Dtos.ConfirmBusinessCardRequest request(String action,Long customerId){return request(action,customerId,"新商機");}
    /** 產生可變商機名的確認請求。 */
    private Dtos.ConfirmBusinessCardRequest request(String action,Long customerId,String opportunity){return new Dtos.ConfirmBusinessCardRequest(action,customerId,"未來科技","buyer@example.com","0912345678","12345678","科技","王小明","採購","buyer@example.com",opportunity,new BigDecimal("1000"),LocalDate.now().plusMonths(1),LocalDateTime.now().plusDays(1));}
    /** 測試用反射填入 generated id。 */
    private void setId(Object entity,Long id){try{var f=entity.getClass().getDeclaredField("id");f.setAccessible(true);f.set(entity,id);}catch(Exception e){throw new RuntimeException(e);}}
    /** JSON canonical payload。 */ private String write(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new RuntimeException(e);}}
    /** SHA-256。 */ private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
}
