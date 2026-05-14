package com.example.situation.model;

import com.example.situation.compliance.DataClassification;
import com.example.situation.compliance.DataClassificationLevel;
import com.example.situation.security.EncryptedBigDecimalAttributeConverter;
import com.example.situation.security.EncryptedLocalDateAttributeConverter;
import com.example.situation.security.EncryptedStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@DataClassification(DataClassificationLevel.CONFIDENTIEL)
@Getter
@Setter
@NoArgsConstructor
public class Situation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "beneficaire", nullable = false, length = 768)
    private String beneficiaire;

    @Size(max = 50)
    @Column(name = "be")
    private String be;

    @Convert(converter = EncryptedLocalDateAttributeConverter.class)
    @Column(name = "date_op", length = 256)
    private LocalDate dateOp;

    @Size(max = 100)
    @Column(name = "n\u00e2\u00b0_ov")
    private String numeroOv;

    @Size(max = 100)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "cheque", length = 512)
    private String cheque;

    @DecimalMin(value = "0.0", inclusive = true)
    @Convert(converter = EncryptedBigDecimalAttributeConverter.class)
    @Column(name = "montant_ov/cheque", length = 256)
    private BigDecimal montantOvCheque;

    @Size(max = 100)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "budget", length = 512)
    private String budget;

    @Size(max = 100)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "rubrique_budg", length = 512)
    private String rubriqueBudg;

    @Size(max = 100)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "n\u00e2\u00b0_op", length = 512)
    private String numeroOp;

    @DecimalMin(value = "0.0", inclusive = true)
    @Convert(converter = EncryptedBigDecimalAttributeConverter.class)
    @Column(name = "montant_op", length = 256)
    private BigDecimal montantOp;

    @Size(max = 255)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "objet_d\u00e3\u00a9pense", length = 768)
    private String objetDepense;

    @Size(max = 30)
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "annee_d'origine", length = 256)
    private String anneeOrigine;

    @Size(max = 100)
    @Column(name = "situation")
    private String situation;

    @Convert(converter = EncryptedLocalDateAttributeConverter.class)
    @Column(name = "date_virement", length = 256)
    private LocalDate dateVirement;

    @Size(max = 120)
    @Column(name = "projet")
    private String projet;

    @Size(max = 1000)
    @Pattern(regexp = "^(https://.*)?$", message = "beUrl must start with https://")
    @Column(name = "be_url")
    private String beUrl;
}
