package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.businesscard.BusinessCardConflictException;
import com.aicrm.crm.service.media.*;
import com.aicrm.crm.service.vision.BusinessCardRecognitionClient;
import com.aicrm.crm.support.PostgresTestBase;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.*;

/** 真實 PostgreSQL 鎖與唯一約束下的名片確認併發冪等測試。 */
@org.springframework.boot.test.context.SpringBootTest(properties={"app.media.enabled=true","app.media.s3.endpoint=http://unused","app.media.s3.access-key=x","app.media.s3.secret-key=x"})
class BusinessCardIdempotencyConcurrencyIntegrationTest extends PostgresTestBase {
    @Autowired BusinessCardIntakeService service; @Autowired SystemSettingService settings;
    @Autowired AiProviderRepository providers; @Autowired AppUserRepository users; @Autowired CustomerRepository customers;
    @Autowired ContactRepository contacts; @Autowired OpportunityRepository opportunities; @Autowired CrmTaskRepository tasks;
    @Autowired JdbcTemplate jdbc; @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean TemporaryMediaStore store; @MockitoBean BusinessCardRecognitionClient vision;
    private byte[] png; private JwtService.AuthPrincipal principal; private Customer mergeCustomer;

    /** 建立 Vision assignment、owner customer 與 deterministic 媒體 fake。 */
    @BeforeEach void setup() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);png=out.toByteArray();
        var owner=users.findByUsername("sales@aurora.local").orElseThrow();principal=new JwtService.AuthPrincipal(owner.getUsername(),owner.getDisplayName(),owner.getRole());
        String suffix=String.format("%08d",Math.abs(UUID.randomUUID().hashCode())%100_000_000);
        mergeCustomer=new Customer("併發測試客戶"+suffix,"merge"+suffix+"@example.com","09"+suffix,
                suffix,"科技",owner.getDisplayName());mergeCustomer.assignOwner(owner);mergeCustomer=customers.save(mergeCustomer);
        var provider=providers.findAll().stream().filter(p->p.getName().equals("ConcurrencyCardTest")).findFirst().orElseGet(()->providers.save(new AiProvider("ConcurrencyCardTest","http://unused","secret","test")));
        settings.updateAiSettings("",null,List.of(new Dtos.ModelOptionItem("vision-concurrency",provider.getId(),Set.of(),CapabilitySource.UNKNOWN)),null,null,null,"test");
        settings.updateModelCapabilities("vision-concurrency",provider.getId(),Set.of(ModelCapability.VISION),"test");
        settings.updateAssignments(new Dtos.AiModelAssignments("",null,"vision-concurrency",provider.getId(),"",null),"test");
        when(store.put(any())).thenAnswer(inv->{MediaUpload u=inv.getArgument(0);return new StoredMedia("temporary/"+UUID.randomUUID(),u.bytes().length,"0".repeat(64));});
        when(store.get(anyString())).thenAnswer(inv->new ByteArrayInputStream(png));
        when(vision.recognize(any(),anyString(),any())).thenReturn(new Dtos.RecognizedBusinessCard("王小明","採購","buyer@example.com","0912345678","測試公司",null,Map.of(),List.of()));
    }

    /** 同 key 同 payload 雙執行緒只建立一組 CRM，兩者回相同結果。 */
    @Test void sameKeySamePayload_replaysSingleCommittedResult() throws Exception {
        long first=createIntake(), second=createIntake(), c0=contacts.count(),o0=opportunities.count(),t0=tasks.count();
        var results=runPair(first,second,request("併發同內容"),semanticEquivalent("併發同內容"));
        assertThat(results).allMatch(Result::success); assertThat(results.get(0).response()).isEqualTo(results.get(1).response());
        assertThat(contacts.count()).isEqualTo(c0+1);assertThat(opportunities.count()).isEqualTo(o0+1);assertThat(tasks.count()).isEqualTo(t0+1);
    }

    /** CREATE 忽略輸入 customerId，跨 intake 仍只建立一個新客戶與一組 CRM。 */
    @Test void create_ignoredCustomerIdDifferenceReplays() throws Exception {
        long first=createIntake(),second=createIntake(),c0=customers.count(),o0=opportunities.count();
        var base=createRequest(null,"CREATE canonical");var ignoredId=createRequest(mergeCustomer.getId(),"CREATE canonical");
        var results=runPair(first,second,base,ignoredId);
        assertThat(results).allMatch(Result::success);assertThat(results.get(0).response()).isEqualTo(results.get(1).response());
        assertThat(customers.count()).isEqualTo(c0+1);assertThat(opportunities.count()).isEqualTo(o0+1);
    }

    /** 同 key 不同 payload 只能一筆成功，另一筆衝突且仍只建立一組 CRM。 */
    @Test void sameKeyDifferentPayload_oneWinsOneConflictsWithoutPartialDuplicate() throws Exception {
        long first=createIntake(),second=createIntake(),c0=contacts.count(),o0=opportunities.count(),t0=tasks.count();
        var results=runPair(first,second,request("版本A"),request("版本B"));
        assertThat(results.stream().filter(Result::success)).hasSize(1);assertThat(results.stream().filter(r->r.error() instanceof BusinessCardConflictException)).hasSize(1);
        assertThat(contacts.count()).isEqualTo(c0+1);assertThat(opportunities.count()).isEqualTo(o0+1);assertThat(tasks.count()).isEqualTo(t0+1);
    }

    /** V23.2 CHECK 在資料庫層拒絕缺少結果欄位的假 CONFIRMED。 */
    @Test void confirmationConstraintRejectsIncompleteResult(){
        long intake=createIntake();
        assertThatThrownBy(()->jdbc.update("update business_card_intakes set status='CONFIRMED' where id=?",intake))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** @Version 使兩個 persistence context 的第二次提交發生 optimistic conflict。 */
    @Test void versionRejectsLostUpdate(){
        long id=createIntake(); EntityManager first=entityManagerFactory.createEntityManager(),second=entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();second.getTransaction().begin();
            first.find(BusinessCardIntake.class,id).fail("first");second.find(BusinessCardIntake.class,id).fail("second");
            first.getTransaction().commit();
            assertThatThrownBy(()->second.getTransaction().commit()).isInstanceOf(RollbackException.class);
        } finally {if(first.getTransaction().isActive())first.getTransaction().rollback();if(second.getTransaction().isActive())second.getTransaction().rollback();first.close();second.close();}
    }

    /** 以 barrier 同時送出兩筆確認。 */
    private List<Result> runPair(long first,long second,Dtos.ConfirmBusinessCardRequest a,Dtos.ConfirmBusinessCardRequest b) throws Exception {
        CyclicBarrier barrier=new CyclicBarrier(2);ExecutorService executor=Executors.newFixedThreadPool(2);
        String key="same-key-"+first;
        try {var f1=executor.submit(()->invoke(barrier,first,a,key));var f2=executor.submit(()->invoke(barrier,second,b,key));return List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));}
        finally {executor.shutdownNow();}
    }
    /** 單一併發呼叫轉成可斷言結果。 */
    private Result invoke(CyclicBarrier barrier,long intake,Dtos.ConfirmBusinessCardRequest request,String key){try{barrier.await();return new Result(service.confirm(intake,request,principal,key),null);}catch(Throwable e){return new Result(null,e);}}
    /** 建立 REVIEW_PENDING intake。 */
    private long createIntake(){return service.create(new MockMultipartFile("file","card.png","image/png",png),principal).id();}
    /** 建立固定 hash 輸入，僅商機名可變。 */
    private Dtos.ConfirmBusinessCardRequest request(String name){return new Dtos.ConfirmBusinessCardRequest("MERGE",mergeCustomer.getId(),mergeCustomer.getName(),mergeCustomer.getEmail(),mergeCustomer.getPhone(),mergeCustomer.getTaxId(),mergeCustomer.getIndustry(),"王小明","採購","buyer@example.com",name,new BigDecimal("1000"),LocalDate.of(2026,12,1),LocalDateTime.of(2026,8,1,10,0));}
    /** 空白、action case、email case、電話格式及金額 scale 不影響 canonical payload。 */
    private Dtos.ConfirmBusinessCardRequest semanticEquivalent(String name){return new Dtos.ConfirmBusinessCardRequest(" merge ",mergeCustomer.getId(),"ignored customer","not-an-email","x","ignored-tax","ignored-industry"," 王小明 "," 採購 "," BUYER@EXAMPLE.COM "," "+name+" ",new BigDecimal("1000.00"),LocalDate.of(2026,12,1),LocalDateTime.of(2026,8,1,10,0));}
    /** 建立 CREATE command，customerId 是應被忽略的輸入。 */
    private Dtos.ConfirmBusinessCardRequest createRequest(Long ignoredCustomerId,String name){return new Dtos.ConfirmBusinessCardRequest(" create ",ignoredCustomerId,"新客戶","new@example.com","+886 912-345-678","87654321","科技","王小明","採購","buyer@example.com",name,new BigDecimal("1000.0"),LocalDate.of(2026,12,1),LocalDateTime.of(2026,8,1,10,0));}
    /** 併發呼叫結果。 */ private record Result(Dtos.BusinessCardConfirmResponse response,Throwable error){boolean success(){return response!=null;}}
}
