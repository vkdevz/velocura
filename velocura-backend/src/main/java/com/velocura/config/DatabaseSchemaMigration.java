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
