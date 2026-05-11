package com.example.situation.controller;

import com.example.situation.dto.SituationKpiResponse;
import com.example.situation.model.Situation;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ModelSanitizer;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import com.example.situation.service.SituationKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/situations")
@Tag(name = "Situations API")
@Validated
public class SituationApiController {

    private final SituationRepository situationRepository;
    private final ModelSanitizer modelSanitizer;
    private final ProjetAccessService projetAccessService;
    private final SituationKpiService situationKpiService;

    public SituationApiController(
        SituationRepository situationRepository,
        ModelSanitizer modelSanitizer,
        ProjetAccessService projetAccessService,
        SituationKpiService situationKpiService
    ) {
        this.situationRepository = situationRepository;
        this.modelSanitizer = modelSanitizer;
        this.projetAccessService = projetAccessService;
        this.situationKpiService = situationKpiService;
    }

    @GetMapping
    @Operation(summary = "List all situations")
    public List<Situation> findAll(Authentication authentication) {
        ProjetAccessScope scope = resolveScope(authentication);
        return switch (scope) {
            case ALL -> situationRepository.findAll();
            case DDZA_ONLY, DDZO_ONLY -> situationRepository.findByProjetContainingIgnoreCase(
                projetAccessService.requiredProjetToken(scope)
            );
            case NONE -> throw forbiddenNoProjetScope();
        };
    }

    @GetMapping("/kpi/status")
    @Operation(summary = "Get status KPI distribution for current user's projet scope")
    public SituationKpiResponse getStatusKpi(Authentication authentication) {
        return situationKpiService.buildStatusKpi(authentication);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one situation by id")
    public Situation findById(@PathVariable @Positive Long id, Authentication authentication) {
        ProjetAccessScope scope = resolveScope(authentication);
        Situation situation = situationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid situation id: " + id));
        assertCanAccessProjet(scope, situation.getProjet());
        return situation;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a situation")
    public Situation create(@Valid @RequestBody Situation situation, Authentication authentication) {
        modelSanitizer.sanitizeSituation(situation);
        ProjetAccessScope scope = resolveScope(authentication);
        assertCanAccessProjet(scope, situation.getProjet());
        return situationRepository.save(situation);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a situation")
    public Situation update(
        @PathVariable @Positive Long id,
        @Valid @RequestBody Situation input,
        Authentication authentication
    ) {
        modelSanitizer.sanitizeSituation(input);
        ProjetAccessScope scope = resolveScope(authentication);
        Situation existing = situationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid situation id: " + id));
        assertCanAccessProjet(scope, existing.getProjet());
        assertCanAccessProjet(scope, input.getProjet());

        existing.setBeneficiaire(input.getBeneficiaire());
        existing.setBe(input.getBe());
        existing.setDateOp(input.getDateOp());
        existing.setNumeroOv(input.getNumeroOv());
        existing.setCheque(input.getCheque());
        existing.setMontantOvCheque(input.getMontantOvCheque());
        existing.setBudget(input.getBudget());
        existing.setRubriqueBudg(input.getRubriqueBudg());
        existing.setNumeroOp(input.getNumeroOp());
        existing.setMontantOp(input.getMontantOp());
        existing.setObjetDepense(input.getObjetDepense());
        existing.setAnneeOrigine(input.getAnneeOrigine());
        existing.setSituation(input.getSituation());
        existing.setDateVirement(input.getDateVirement());
        existing.setProjet(input.getProjet());
        existing.setBeUrl(input.getBeUrl());

        return situationRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a situation")
    public void delete(@PathVariable @Positive Long id, Authentication authentication) {
        ProjetAccessScope scope = resolveScope(authentication);
        Situation existing = situationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid situation id: " + id));
        assertCanAccessProjet(scope, existing.getProjet());
        situationRepository.deleteById(id);
    }

    private ProjetAccessScope resolveScope(Authentication authentication) {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            throw forbiddenNoProjetScope();
        }
        return scope;
    }

    private void assertCanAccessProjet(ProjetAccessScope scope, String projet) {
        if (!projetAccessService.canAccess(scope, projet)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this projet");
        }
    }

    private static ResponseStatusException forbiddenNoProjetScope() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "No projet scope assigned to this user");
    }
}
