package com.velocura.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Order(1)
public class DatabaseSchemaMigration implements CommandLineRunner {

    private final DataSource dataSource;

    @Autowired
    public DatabaseSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        System.out.println("SCHEMA MIGRATION: Checking and updating database schema...");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Ensure columns exist on 'users' table
            addColumnIfNotExists(stmt, "users", "auth_provider", "VARCHAR(255) DEFAULT 'LOCAL'");
            addColumnIfNotExists(stmt, "users", "google_id", "VARCHAR(255)");
            addColumnIfNotExists(stmt, "users", "profile_picture", "VARCHAR(1024)");
            addColumnIfNotExists(stmt, "users", "is_active", "BOOLEAN DEFAULT TRUE");
            addColumnIfNotExists(stmt, "users", "is_deleted", "BOOLEAN DEFAULT FALSE");

            // Backfill any null auth_provider records
            try {
                stmt.execute("UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL");
            } catch (Exception ignored) {}

            // 2. Ensure columns on 'doctors' table
            addColumnIfNotExists(stmt, "doctors", "is_verified", "BOOLEAN DEFAULT FALSE");

            // 3. Ensure columns on 'patients' table
            addColumnIfNotExists(stmt, "patients", "allergies", "TEXT");
            addColumnIfNotExists(stmt, "patients", "medical_history_timeline", "TEXT");

            // 4. Ensure appointments and clinical_sessions have optimistic locking version column and patient ownership columns
            addColumnIfNotExists(stmt, "appointments", "version", "BIGINT DEFAULT 0");
            addColumnIfNotExists(stmt, "clinical_sessions", "version", "BIGINT DEFAULT 0");
            addColumnIfNotExists(stmt, "clinical_sessions", "patient_id", "BIGINT");
            addColumnIfNotExists(stmt, "clinical_sessions", "patient_email", "VARCHAR(128)");

            // 5. Ensure consultation_messages table exists
            try {
                stmt.execute("CREATE TABLE IF NOT EXISTS consultation_messages ("
                        + "id BIGSERIAL PRIMARY KEY, "
                        + "appointment_id BIGINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, "
                        + "recipient_id BIGINT, "
                        + "content TEXT NOT NULL, "
                        + "message_type VARCHAR(32) DEFAULT 'TEXT', "
                        + "created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
            } catch (Exception ignored) {}

            // 6. Ensure high-performance query indexes exist
            createIndexIfNotExists(stmt, "idx_appts_doc_time", "appointments", "doctor_id, appointment_time");
            createIndexIfNotExists(stmt, "idx_appts_patient", "appointments", "patient_id");
            createIndexIfNotExists(stmt, "idx_consult_appt", "consultation_messages", "appointment_id");
            createIndexIfNotExists(stmt, "idx_presc_patient", "prescriptions", "patient_id");
            createIndexIfNotExists(stmt, "idx_clin_sess_patient_id", "clinical_sessions", "patient_id");
            createIndexIfNotExists(stmt, "idx_clin_sess_patient_email", "clinical_sessions", "patient_email");
            createIndexIfNotExists(stmt, "idx_users_google_id", "users", "google_id");
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_id_unique ON users (google_id) WHERE google_id IS NOT NULL");
            } catch (Exception ignored) {}

            // 7. Ensure Medical Knowledge Engine indexes exist
            createIndexIfNotExists(stmt, "idx_m_concept_name", "medical_concepts", "canonical_name");
            createIndexIfNotExists(stmt, "idx_m_concept_type", "medical_concepts", "concept_type");
            createIndexIfNotExists(stmt, "idx_m_concept_status", "medical_concepts", "status");
            createIndexIfNotExists(stmt, "idx_m_concept_jurisdiction", "medical_concepts", "jurisdiction");
            createIndexIfNotExists(stmt, "idx_m_concept_batch", "medical_concepts", "batch_id");
            createIndexIfNotExists(stmt, "idx_synonym_text", "medical_concept_synonyms", "synonym");
            createIndexIfNotExists(stmt, "idx_term_sys_code", "terminology_mappings", "terminology_system, code");
            createIndexIfNotExists(stmt, "idx_rel_source_type", "medical_relationships", "source_concept_id, relationship_type");
            createIndexIfNotExists(stmt, "idx_rel_target_type", "medical_relationships", "target_concept_id, relationship_type");
            createIndexIfNotExists(stmt, "idx_rel_status", "medical_relationships", "status");
            createIndexIfNotExists(stmt, "idx_rel_batch", "medical_relationships", "batch_id");
            createIndexIfNotExists(stmt, "idx_batch_status", "knowledge_import_batches", "status");
            createIndexIfNotExists(stmt, "idx_snap_status", "knowledge_snapshots", "status");
            createIndexIfNotExists(stmt, "idx_quar_batch", "quarantine_records", "batch_id");
            createIndexIfNotExists(stmt, "idx_quar_reason", "quarantine_records", "reason");
            createIndexIfNotExists(stmt, "idx_conf_subject", "knowledge_conflicts", "subject_concept_id");
            createIndexIfNotExists(stmt, "idx_conf_status", "knowledge_conflicts", "status");

            // 8. Medical Intelligence Fabric - Extended Fields & Indexes
            addColumnIfNotExists(stmt, "medical_relationships", "assertion_type", "VARCHAR(32) DEFAULT 'SOURCE_FACT'");
            addColumnIfNotExists(stmt, "medical_relationships", "population", "VARCHAR(128)");
            addColumnIfNotExists(stmt, "medical_relationships", "age_min_years", "INTEGER");
            addColumnIfNotExists(stmt, "medical_relationships", "age_max_years", "INTEGER");
            addColumnIfNotExists(stmt, "medical_relationships", "sex_applicability", "VARCHAR(16) DEFAULT 'ALL'");
            addColumnIfNotExists(stmt, "medical_relationships", "guideline_reference", "VARCHAR(255)");
            addColumnIfNotExists(stmt, "medical_relationships", "evidence_strength", "VARCHAR(32)");

            addColumnIfNotExists(stmt, "knowledge_sources", "intended_use", "VARCHAR(255)");
            addColumnIfNotExists(stmt, "knowledge_sources", "redistribution_status", "VARCHAR(32) DEFAULT 'REVIEW_REQUIRED'");
            addColumnIfNotExists(stmt, "knowledge_sources", "commercial_use_status", "VARCHAR(32) DEFAULT 'REVIEW_REQUIRED'");
            addColumnIfNotExists(stmt, "knowledge_sources", "derivatives_permitted", "BOOLEAN DEFAULT FALSE");
            addColumnIfNotExists(stmt, "knowledge_sources", "attribution_required", "BOOLEAN DEFAULT TRUE");
            addColumnIfNotExists(stmt, "knowledge_sources", "license_verified", "BOOLEAN DEFAULT FALSE");
            addColumnIfNotExists(stmt, "knowledge_sources", "superseded_by_source_id", "VARCHAR(64)");

            addColumnIfNotExists(stmt, "medical_concept_synonyms", "synonym_type", "VARCHAR(32) DEFAULT 'OFFICIAL_SYNONYM'");
            addColumnIfNotExists(stmt, "medical_concept_synonyms", "match_status", "VARCHAR(32) DEFAULT 'CONFIRMED'");
            addColumnIfNotExists(stmt, "medical_concept_synonyms", "source_version", "VARCHAR(64)");

            addColumnIfNotExists(stmt, "terminology_mappings", "mapping_provenance", "VARCHAR(255)");
            addColumnIfNotExists(stmt, "terminology_mappings", "jurisdiction", "VARCHAR(32) DEFAULT 'GLOBAL'");
            addColumnIfNotExists(stmt, "terminology_mappings", "status", "VARCHAR(32) DEFAULT 'VALID'");

            createIndexIfNotExists(stmt, "idx_raw_src_ver", "raw_source_artifacts", "source_id, version");
            createIndexIfNotExists(stmt, "idx_raw_hash", "raw_source_artifacts", "artifact_hash");
            createIndexIfNotExists(stmt, "idx_evid_topic", "clinical_evidence_records", "topic");
            createIndexIfNotExists(stmt, "idx_evid_source", "clinical_evidence_records", "source");
            createIndexIfNotExists(stmt, "idx_evid_jurisdiction", "clinical_evidence_records", "jurisdiction");
            createIndexIfNotExists(stmt, "idx_evid_status", "clinical_evidence_records", "status");

            System.out.println("SCHEMA MIGRATION: Schema migration executed successfully!");
        } catch (Exception e) {
            System.err.println("SCHEMA MIGRATION WARNING: " + e.getMessage());
        }
    }

    private void createIndexIfNotExists(Statement stmt, String indexName, String tableName, String columns) {
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columns + ")");
        } catch (Exception ignored) {}
    }

    private void addColumnIfNotExists(Statement stmt, String tableName, String columnName, String columnDefinition) {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + columnDefinition);
        } catch (Exception e) {
            try {
                stmt.execute("ALTER TABLE " + tableName.toUpperCase() + " ADD COLUMN IF NOT EXISTS " + columnName.toUpperCase() + " " + columnDefinition);
            } catch (Exception fallbackErr) {
                // If table doesn't exist yet, Hibernate ddl-auto will create it
            }
        }
    }
}
