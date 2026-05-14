package com.example.situation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseSchemaRepairRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaRepairRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaRepairRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            boolean hasAppUser = tableExists("app_user");
            boolean hasSituation = tableExists("situation");

            if (!hasAppUser && !hasSituation) {
                log.info("Schema repair skipped because no target tables are present yet.");
                return;
            }

            if (hasAppUser) {
                repairAppUserTable();
                repairRefreshTokenTable();
            }
            if (hasSituation) {
                repairSituationTable();
            }
        } catch (DataAccessException ex) {
            throw new IllegalStateException(
                "Database schema repair failed. The deployed PostgreSQL schema is missing required auth/MFA columns or tables.",
                ex
            );
        }
    }

    private void repairAppUserTable() {
        addColumnIfMissing("app_user", "failed_login_attempts", "integer NOT NULL DEFAULT 0");
        addColumnIfMissing("app_user", "locked_until", "timestamp with time zone");
        addColumnIfMissing("app_user", "mfa_enabled", "boolean NOT NULL DEFAULT false");
        addColumnIfMissing("app_user", "mfa_secret", "varchar(128)");
        addColumnIfMissing("app_user", "privacy_notice_version", "varchar(40)");
        addColumnIfMissing("app_user", "privacy_notice_accepted_at", "timestamp with time zone");
        addColumnIfMissing("app_user", "deletion_requested_at", "timestamp with time zone");
        addColumnIfMissing("app_user", "deletion_request_reason", "varchar(500)");

        jdbcTemplate.execute("UPDATE \"app_user\" SET \"failed_login_attempts\" = 0 WHERE \"failed_login_attempts\" IS NULL");
        jdbcTemplate.execute("UPDATE \"app_user\" SET \"mfa_enabled\" = false WHERE \"mfa_enabled\" IS NULL");
        jdbcTemplate.execute("ALTER TABLE \"app_user\" ALTER COLUMN \"failed_login_attempts\" SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE \"app_user\" ALTER COLUMN \"failed_login_attempts\" SET NOT NULL");
        jdbcTemplate.execute("ALTER TABLE \"app_user\" ALTER COLUMN \"mfa_enabled\" SET DEFAULT false");
        jdbcTemplate.execute("ALTER TABLE \"app_user\" ALTER COLUMN \"mfa_enabled\" SET NOT NULL");
    }

    private void repairRefreshTokenTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS "refresh_token" (
                "id" BIGSERIAL PRIMARY KEY,
                "app_user_id" BIGINT NOT NULL,
                "token_hash" VARCHAR(64) NOT NULL,
                "issued_at" TIMESTAMP WITH TIME ZONE NOT NULL,
                "expires_at" TIMESTAMP WITH TIME ZONE NOT NULL,
                "revoked_at" TIMESTAMP WITH TIME ZONE,
                "replaced_by_token_hash" VARCHAR(64),
                "created_by_ip" VARCHAR(128),
                "user_agent" VARCHAR(255)
            )
            """
        );

        if (!constraintExists("refresh_token", "fk_refresh_token_app_user")) {
            jdbcTemplate.execute(
                """
                ALTER TABLE "refresh_token"
                ADD CONSTRAINT "fk_refresh_token_app_user"
                FOREIGN KEY ("app_user_id") REFERENCES "app_user" ("id")
                """
            );
        }

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS \"idx_refresh_token_hash\" ON \"refresh_token\" (\"token_hash\")");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS \"idx_refresh_token_expires\" ON \"refresh_token\" (\"expires_at\")");
    }

    private void repairSituationTable() {
        alterColumnType("beneficaire", "varchar(768)");
        alterColumnType("date_op", "varchar(256)");
        alterColumnType("cheque", "varchar(512)");
        alterColumnType("montant_ov/cheque", "varchar(256)");
        alterColumnType("budget", "varchar(512)");
        alterColumnType("rubrique_budg", "varchar(512)");
        alterColumnType("n\u00e2\u00b0_op", "varchar(512)");
        alterColumnType("montant_op", "varchar(256)");
        alterColumnType("objet_d\u00e3\u00a9pense", "varchar(768)");
        alterColumnType("annee_d'origine", "varchar(256)");
        alterColumnType("date_virement", "varchar(256)");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        jdbcTemplate.execute(
            "ALTER TABLE \"" + tableName + "\" ADD COLUMN IF NOT EXISTS \"" + columnName + "\" " + definition
        );
    }

    private void alterColumnType(String columnName, String type) {
        jdbcTemplate.execute(
            "ALTER TABLE \"situation\" ALTER COLUMN \"" + columnName + "\" TYPE " + type
        );
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

    private boolean constraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = current_schema()
              AND t.relname = ?
              AND c.conname = ?
            """,
            Integer.class,
            tableName,
            constraintName
        );
        return count != null && count > 0;
    }
}
