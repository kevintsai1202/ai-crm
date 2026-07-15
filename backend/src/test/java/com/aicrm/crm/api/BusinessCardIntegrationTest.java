package com.aicrm.crm.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AiProviderRepository;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.service.SystemSettingService;
import com.aicrm.crm.service.media.*;
import com.aicrm.crm.service.vision.BusinessCardRecognitionClient;
import com.aicrm.crm.support.PostgresTestBase;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 名片 API 的 multipart、模型 assignment 與水平權限整合測試。 */
@org.springframework.boot.test.context.SpringBootTest(properties={"app.media.enabled=true","app.media.s3.endpoint=http://unused","app.media.s3.access-key=x","app.media.s3.secret-key=x"})
class BusinessCardIntegrationTest extends PostgresTestBase {
    @Autowired WebApplicationContext context; @Autowired FilterChainProxy filters;
    @Autowired SystemSettingService settings; @Autowired AiProviderRepository providers; @Autowired AppUserRepository users;
    @MockitoBean TemporaryMediaStore store; @MockitoBean BusinessCardRecognitionClient vision;
    private MockMvc mvc; private final ObjectMapper mapper=new ObjectMapper(); private byte[] png;

    /** 建立真實 security MockMvc、Vision assignment 與 deterministic fake。 */
    @BeforeEach void setup() throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out); png=out.toByteArray();
        var provider=providers.findAll().stream().filter(p->p.getName().equals("CardTest")).findFirst().orElseGet(()->providers.save(new AiProvider("CardTest","http://unused","secret","test")));
        var option=new Dtos.ModelOptionItem("vision-test",provider.getId(),Set.of(),CapabilitySource.UNKNOWN);
        settings.updateAiSettings("",null,List.of(option),null,null,null,"test");
        settings.updateModelCapabilities("vision-test",provider.getId(),Set.of(ModelCapability.VISION),"test");
        settings.updateAssignments(new Dtos.AiModelAssignments("",null,"vision-test",provider.getId(),"",null),"test");
        when(store.put(any())).thenAnswer(inv->{MediaUpload u=inv.getArgument(0);return new StoredMedia("temporary/"+UUID.randomUUID(),u.bytes().length,"0".repeat(64));});
        when(store.get(anyString())).thenAnswer(inv->new ByteArrayInputStream(png));
        when(vision.recognize(any(),eq("image/png"),any())).thenReturn(new Dtos.RecognizedBusinessCard("王小明","採購","buyer@example.com","0912345678","測試公司",null,Map.of(),List.of()));
    }

    /** 有效 PNG 可建立 intake；其他 SALES 讀取同 id 依防枚舉慣例回 404。 */
    @Test void uploadThenCrossOwnerGet_returnsCreatedAndNotFound() throws Exception {
        String otherUsername=users.findByRole(Role.SALES).stream().map(AppUser::getUsername).filter(u->!u.equals("sales@aurora.local")).findFirst().orElseThrow();
        String ownerToken=login("sales@aurora.local"), otherToken=login(otherUsername);
        var file=new MockMultipartFile("file","card.png","image/png",png);
        String body=mvc.perform(multipart("/api/business-card-intakes").file(file).header("Authorization","Bearer "+ownerToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REVIEW_PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id=mapper.readTree(body).get("id").asLong();
        mvc.perform(get("/api/business-card-intakes/{id}",id).header("Authorization","Bearer "+otherToken)).andExpect(status().isNotFound());
    }

    /** 未登入 multipart 上傳必須在服務前被拒絕。 */
    @Test void uploadWithoutAuthentication_returnsUnauthorized() throws Exception {
        mvc.perform(multipart("/api/business-card-intakes").file(new MockMultipartFile("file","card.png","image/png",png))).andExpect(status().isUnauthorized());
        verifyNoInteractions(vision);
    }

    /** 登入 seed 使用者並取得 JWT。 */
    private String login(String username) throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+username+"\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }
}
