package com.example.situation.service;

import com.example.situation.dto.KpiSlice;
import com.example.situation.dto.SituationKpiResponse;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SituationKpiService {

    private final SituationRepository situationRepository;
    private final ProjetAccessService projetAccessService;

    public SituationKpiService(SituationRepository situationRepository, ProjetAccessService projetAccessService) {
        this.situationRepository = situationRepository;
        this.projetAccessService = projetAccessService;
    }

    public SituationKpiResponse buildStatusKpi(Authentication authentication) {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            throw forbiddenNoProjetScope();
        }

        List<String> rawStatuses = switch (scope) {
            case ALL -> situationRepository.findAllSituationValues();
            case DDZA_ONLY, DDZO_ONLY -> situationRepository.findSituationValuesByProjetContainingIgnoreCase(
                projetAccessService.requiredProjetToken(scope)
            );
            case NONE -> throw forbiddenNoProjetScope();
        };

        long payed = 0;
        long rejected = 0;
        long canceled = 0;
        long other = 0;

        for (String status : rawStatuses) {
            switch (classify(status)) {
                case PAYED -> payed++;
                case REJECTED -> rejected++;
                case CANCELED -> canceled++;
                case OTHER -> other++;
            }
        }

        long total = payed + rejected + canceled + other;
        List<KpiSlice> chart = new ArrayList<>();
        chart.add(new KpiSlice("PAYED", payed, percentage(payed, total)));
        chart.add(new KpiSlice("REJECTED", rejected, percentage(rejected, total)));
        chart.add(new KpiSlice("CANCELED", canceled, percentage(canceled, total)));
        chart.add(new KpiSlice("OTHER", other, percentage(other, total)));

        return new SituationKpiResponse(scope.name(), total, payed, rejected, canceled, other, chart);
    }

    private static double percentage(long value, long total) {
        if (total == 0) {
            return 0d;
        }
        return BigDecimal.valueOf((value * 100.0d) / total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static StatusBucket classify(String rawStatus) {
        String normalized = normalize(rawStatus);
        if (normalized.contains("PAY")) {
            return StatusBucket.PAYED;
        }
        if (normalized.contains("REJECT") || normalized.contains("REJET") || normalized.contains("REFUS")) {
            return StatusBucket.REJECTED;
        }
        if (normalized.contains("ANNUL") || normalized.contains("ANUL") || normalized.contains("CANCEL")) {
            return StatusBucket.CANCELED;
        }
        return StatusBucket.OTHER;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT)
            .trim();
    }

    private static ResponseStatusException forbiddenNoProjetScope() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "No projet scope assigned to this user");
    }

    private enum StatusBucket {
        PAYED,
        REJECTED,
        CANCELED,
        OTHER
    }
}
