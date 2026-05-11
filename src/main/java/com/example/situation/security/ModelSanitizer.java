package com.example.situation.security;

import com.example.situation.model.AppUser;
import com.example.situation.model.Situation;
import org.springframework.stereotype.Component;

@Component
public class ModelSanitizer {

    private final InputSanitizer inputSanitizer;

    public ModelSanitizer(InputSanitizer inputSanitizer) {
        this.inputSanitizer = inputSanitizer;
    }

    public void sanitizeSituation(Situation s) {
        if (s == null) {
            return;
        }
        s.setBeneficiaire(inputSanitizer.sanitize(s.getBeneficiaire()));
        s.setBe(inputSanitizer.sanitize(s.getBe()));
        s.setNumeroOv(inputSanitizer.sanitize(s.getNumeroOv()));
        s.setCheque(inputSanitizer.sanitize(s.getCheque()));
        s.setBudget(inputSanitizer.sanitize(s.getBudget()));
        s.setRubriqueBudg(inputSanitizer.sanitize(s.getRubriqueBudg()));
        s.setNumeroOp(inputSanitizer.sanitize(s.getNumeroOp()));
        s.setObjetDepense(inputSanitizer.sanitize(s.getObjetDepense()));
        s.setAnneeOrigine(inputSanitizer.sanitize(s.getAnneeOrigine()));
        s.setSituation(inputSanitizer.sanitize(s.getSituation()));
        s.setProjet(inputSanitizer.sanitize(s.getProjet()));
        s.setBeUrl(inputSanitizer.sanitize(s.getBeUrl()));
    }

    public void sanitizeUser(AppUser u) {
        if (u == null) {
            return;
        }
        u.setUsername(inputSanitizer.sanitize(u.getUsername()));
        u.setRole(inputSanitizer.sanitize(u.getRole()));
        u.setPassword(inputSanitizer.sanitize(u.getPassword()));
    }
}
