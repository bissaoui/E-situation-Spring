package com.example.situation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "situation")
@Getter
@Setter
@NoArgsConstructor
public class Situation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "BENEFICAIRE", nullable = false)
    private String beneficiaire;

    @Size(max = 50)
    @Column(name = "BE")
    private String be;

    @Column(name = "DATE_OP")
    private LocalDate dateOp;

    @Size(max = 100)
    @Column(name = "NÂ°_OV")
    private String numeroOv;

    @Size(max = 100)
    @Column(name = "CHEQUE")
    private String cheque;

    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "MONTANT_OV/CHEQUE", precision = 19, scale = 2)
    private BigDecimal montantOvCheque;

    @Size(max = 100)
    @Column(name = "BUDGET")
    private String budget;

    @Size(max = 100)
    @Column(name = "Rubrique_budg")
    private String rubriqueBudg;

    @Size(max = 100)
    @Column(name = "NÂ°_OP")
    private String numeroOp;

    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "Montant_OP", precision = 19, scale = 2)
    private BigDecimal montantOp;

    @Size(max = 255)
    @Column(name = "Objet_dÃ©pense")
    private String objetDepense;

    @Size(max = 30)
    @Column(name = "ANNEE_D'ORIGINE")
    private String anneeOrigine;

    @Size(max = 100)
    @Column(name = "Situation")
    private String situation;

    @Column(name = "Date_Virement")
    private LocalDate dateVirement;

    @Size(max = 120)
    @Column(name = "Projet")
    private String projet;

    @Size(max = 1000)
    @Pattern(regexp = "^(https?://.*)?$", message = "beUrl must start with http:// or https://")
    @Column(name = "BE_URL")
    private String beUrl;
}
