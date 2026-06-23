package com.aicrm.crm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicrm.crm.repository.InteractionInsightRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SentimentService 單元測試：驗證 radar() 不重複呼叫 aggregateCustomerRisk()。
 */
@ExtendWith(MockitoExtension.class)
class SentimentServiceTest {

    @Mock
    InteractionInsightRepository insights;

    @InjectMocks
    SentimentService service;

    /**
     * radar() 內 churnRadar() 與 priorityCare() 共用同一次 aggregateCustomerRisk()，
     * 不應對 DB 發出兩次相同查詢。
     */
    @Test
    void radar_aggregateCustomerRiskCalledOnce() {
        when(insights.countByIntent()).thenReturn(List.of());
        when(insights.sentimentTrendSince(any())).thenReturn(List.of());
        when(insights.findHighRiskInteractions(any())).thenReturn(List.of());
        when(insights.aggregateCustomerRisk()).thenReturn(List.of());

        service.radar();

        verify(insights, times(1)).aggregateCustomerRisk();
    }
}
