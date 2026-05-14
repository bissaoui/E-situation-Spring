package com.example.situation.config;

import com.example.situation.security.FieldEncryptionService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class SituationFieldEncryptionMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SituationFieldEncryptionMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final FieldEncryptionService fieldEncryptionService;
    private final boolean migrateOnStartup;

    public SituationFieldEncryptionMigrationRunner(
        JdbcTemplate jdbcTemplate,
        FieldEncryptionService fieldEncryptionService,
        @Value("${security.data.encryption.migrate-on-startup:true}") boolean migrateOnStartup
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fieldEncryptionService = fieldEncryptionService;
        this.migrateOnStartup = migrateOnStartup;
    }

    @Override
    public void run(String... args) {
        if (!migrateOnStartup || !tableExists("situation")) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT
                "id",
                "beneficaire",
                "date_op",
                "cheque",
                "montant_ov/cheque" AS montant_ov_cheque,
                "budget",
                "rubrique_budg",
                "n\u00e2\u00b0_op" AS numero_op,
                "montant_op",
                "objet_d\u00e3\u00a9pense" AS objet_depense,
                "annee_d'origine" AS annee_origine,
                "date_virement"
            FROM "situation"
            WHERE ("beneficaire" IS NOT NULL AND "beneficaire" <> '' AND "beneficaire" NOT LIKE 'enc:v1:%')
               OR ("date_op" IS NOT NULL AND "date_op" <> '' AND "date_op" NOT LIKE 'enc:v1:%')
               OR ("cheque" IS NOT NULL AND "cheque" <> '' AND "cheque" NOT LIKE 'enc:v1:%')
               OR ("montant_ov/cheque" IS NOT NULL AND "montant_ov/cheque" <> '' AND "montant_ov/cheque" NOT LIKE 'enc:v1:%')
               OR ("budget" IS NOT NULL AND "budget" <> '' AND "budget" NOT LIKE 'enc:v1:%')
               OR ("rubrique_budg" IS NOT NULL AND "rubrique_budg" <> '' AND "rubrique_budg" NOT LIKE 'enc:v1:%')
               OR ("n\u00e2\u00b0_op" IS NOT NULL AND "n\u00e2\u00b0_op" <> '' AND "n\u00e2\u00b0_op" NOT LIKE 'enc:v1:%')
               OR ("montant_op" IS NOT NULL AND "montant_op" <> '' AND "montant_op" NOT LIKE 'enc:v1:%')
               OR ("objet_d\u00e3\u00a9pense" IS NOT NULL AND "objet_d\u00e3\u00a9pense" <> '' AND "objet_d\u00e3\u00a9pense" NOT LIKE 'enc:v1:%')
               OR ("annee_d'origine" IS NOT NULL AND "annee_d'origine" <> '' AND "annee_d'origine" NOT LIKE 'enc:v1:%')
               OR ("date_virement" IS NOT NULL AND "date_virement" <> '' AND "date_virement" NOT LIKE 'enc:v1:%')
            """
        );

        if (rows.isEmpty()) {
            return;
        }

        log.info("Migrating {} situation rows to encrypted protected-field storage.", rows.size());

        for (Map<String, Object> row : rows) {
            jdbcTemplate.update(
                """
                UPDATE "situation"
                SET
                    "beneficaire" = ?,
                    "date_op" = ?,
                    "cheque" = ?,
                    "montant_ov/cheque" = ?,
                    "budget" = ?,
                    "rubrique_budg" = ?,
                    "n\u00e2\u00b0_op" = ?,
                    "montant_op" = ?,
                    "objet_d\u00e3\u00a9pense" = ?,
                    "annee_d'origine" = ?,
                    "date_virement" = ?
                WHERE "id" = ?
                """,
                encryptIfNeeded(row.get("beneficaire")),
                encryptIfNeeded(row.get("date_op")),
                encryptIfNeeded(row.get("cheque")),
                encryptIfNeeded(row.get("montant_ov_cheque")),
                encryptIfNeeded(row.get("budget")),
                encryptIfNeeded(row.get("rubrique_budg")),
                encryptIfNeeded(row.get("numero_op")),
                encryptIfNeeded(row.get("montant_op")),
                encryptIfNeeded(row.get("objet_depense")),
                encryptIfNeeded(row.get("annee_origine")),
                encryptIfNeeded(row.get("date_virement")),
                row.get("id")
            );
        }

        log.info("Situation field-encryption migration completed.");
    }

    private String encryptIfNeeded(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.toString();
        if (value.isBlank() || fieldEncryptionService.isEncrypted(value)) {
            return value;
        }
        return fieldEncryptionService.encrypt(value);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }
}
