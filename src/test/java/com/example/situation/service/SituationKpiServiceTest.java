package com.example.situation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.situation.dto.SituationKpiResponse;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SituationKpiServiceTest {

    @Mock
    private SituationRepository situationRepository;

    @Mock
    private ProjetAccessService projetAccessService;

    @Mock
    private Authentication authentication;

    private SituationKpiService situationKpiService;

    @BeforeEach
    void setUp() {
        situationKpiService = new SituationKpiService(situationRepository, projetAccessService);
    }

    @Test
    void buildStatusKpiUsesProjetScopeForDdzaUser() {
        when(projetAccessService.resolveScope(authentication)).thenReturn(ProjetAccessScope.DDZA_ONLY);
        when(projetAccessService.requiredProjetToken(ProjetAccessScope.DDZA_ONLY)).thenReturn("ddza");
        when(situationRepository.findSituationValuesByProjetContainingIgnoreCase("ddza"))
            .thenReturn(List.of("PAYED", "rejected", "Annulee", "pending", "paye"));

        SituationKpiResponse response = situationKpiService.buildStatusKpi(authentication);

        assertEquals("DDZA_ONLY", response.scope());
        assertEquals(5L, response.total());
        assertEquals(2L, response.payed());
        assertEquals(1L, response.rejected());
        assertEquals(1L, response.canceled());
        assertEquals(1L, response.other());
        verify(situationRepository).findSituationValuesByProjetContainingIgnoreCase("ddza");
        verify(situationRepository, never()).findAllSituationValues();
    }

    @Test
    void buildStatusKpiUsesAllScopeForAdminLikeUser() {
        when(projetAccessService.resolveScope(authentication)).thenReturn(ProjetAccessScope.ALL);
        when(situationRepository.findAllSituationValues()).thenReturn(List.of("PAYED"));

        SituationKpiResponse response = situationKpiService.buildStatusKpi(authentication);

        assertEquals("ALL", response.scope());
        assertEquals(1L, response.total());
        assertEquals(1L, response.payed());
        assertEquals(0L, response.rejected());
        assertEquals(0L, response.canceled());
        assertEquals(0L, response.other());
        verify(situationRepository).findAllSituationValues();
    }

    @Test
    void buildStatusKpiThrowsWhenScopeIsNone() {
        when(projetAccessService.resolveScope(authentication)).thenReturn(ProjetAccessScope.NONE);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> situationKpiService.buildStatusKpi(authentication)
        );

        assertEquals(403, exception.getStatusCode().value());
    }
}
