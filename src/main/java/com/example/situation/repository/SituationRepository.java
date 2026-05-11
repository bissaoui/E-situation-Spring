package com.example.situation.repository;

import com.example.situation.model.Situation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SituationRepository extends JpaRepository<Situation, Long> {
    List<Situation> findByProjetContainingIgnoreCase(String projet);

    Optional<Situation> findByIdAndProjetContainingIgnoreCase(Long id, String projet);

    Optional<Situation> findTopByBeIgnoreCaseOrderByIdDesc(String be);

    Optional<Situation> findTopByNumeroOvIgnoreCaseOrderByIdDesc(String numeroOv);

    Optional<Situation> findTopByBeIgnoreCaseAndProjetContainingIgnoreCaseOrderByIdDesc(String be, String projet);

    Optional<Situation> findTopByNumeroOvIgnoreCaseAndProjetContainingIgnoreCaseOrderByIdDesc(
        String numeroOv,
        String projet
    );

    @Query("select coalesce(s.situation, '') from Situation s")
    List<String> findAllSituationValues();

    @Query(
        "select coalesce(s.situation, '') from Situation s " +
        "where lower(s.projet) like lower(concat('%', :projetToken, '%'))"
    )
    List<String> findSituationValuesByProjetContainingIgnoreCase(@Param("projetToken") String projetToken);
}
