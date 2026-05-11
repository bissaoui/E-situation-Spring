package com.example.situation.controller;

import com.example.situation.model.Situation;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ModelSanitizer;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import com.example.situation.service.SituationImportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SituationController {

    private final SituationRepository situationRepository;
    private final SituationImportService situationImportService;
    private final ModelSanitizer modelSanitizer;
    private final ProjetAccessService projetAccessService;

    public SituationController(
        SituationRepository situationRepository,
        SituationImportService situationImportService,
        ModelSanitizer modelSanitizer,
        ProjetAccessService projetAccessService
    ) {
        this.situationRepository = situationRepository;
        this.situationImportService = situationImportService;
        this.modelSanitizer = modelSanitizer;
        this.projetAccessService = projetAccessService;
    }

    @GetMapping({"/", "/situations"})
    public String list(Model model, Authentication authentication) {
        ProjetAccessScope scope = resolveScope(authentication);
        List<Situation> situations = switch (scope) {
            case ALL -> situationRepository.findAll();
            case DDZA_ONLY, DDZO_ONLY -> situationRepository.findByProjetContainingIgnoreCase(
                projetAccessService.requiredProjetToken(scope)
            );
            case NONE -> throw forbiddenNoProjetScope();
        };

        model.addAttribute("situations", situations);
        return "situations";
    }

    @GetMapping("/situations/new")
    public String newForm(Model model) {
        model.addAttribute("situation", new Situation());
        return "situation-form";
    }

    @PostMapping("/situations")
    public String create(
        @Valid @ModelAttribute("situation") Situation situation,
        BindingResult bindingResult,
        Authentication authentication
    ) {
        modelSanitizer.sanitizeSituation(situation);
        ProjetAccessScope scope = resolveScope(authentication);
        if (!projetAccessService.canAccess(scope, situation.getProjet())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this projet");
        }
        if (bindingResult.hasErrors()) {
            return "situation-form";
        }
        situationRepository.save(situation);
        return "redirect:/situations";
    }

    @PostMapping("/situations/import")
    public String importCsv(
        @RequestParam("file") MultipartFile file,
        RedirectAttributes redirectAttributes,
        Authentication authentication
    ) {
        ProjetAccessScope scope = resolveScope(authentication);
        if (scope != ProjetAccessScope.ALL) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Import is allowed only for users with full projet access"
            );
        }

        try {
            int imported = situationImportService.importFile(file);
            redirectAttributes.addFlashAttribute("successMessage", imported + " row(s) imported successfully.");
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + ex.getMessage());
        }
        return "redirect:/situations";
    }

    private ProjetAccessScope resolveScope(Authentication authentication) {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            throw forbiddenNoProjetScope();
        }
        return scope;
    }

    private static ResponseStatusException forbiddenNoProjetScope() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "No projet scope assigned to this user");
    }
}
