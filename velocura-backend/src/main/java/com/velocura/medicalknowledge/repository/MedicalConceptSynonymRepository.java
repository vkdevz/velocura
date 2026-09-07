package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.MedicalConceptSynonym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalConceptSynonymRepository extends JpaRepository<MedicalConceptSynonym, Long> {

    List<MedicalConceptSynonym> findBySynonymIgnoreCase(String synonym);

    @Query("SELECT s FROM MedicalConceptSynonym s WHERE LOWER(s.synonym) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<MedicalConceptSynonym> searchSynonyms(@Param("query") String query);

    List<MedicalConceptSynonym> findByConceptConceptId(String conceptId);
}
