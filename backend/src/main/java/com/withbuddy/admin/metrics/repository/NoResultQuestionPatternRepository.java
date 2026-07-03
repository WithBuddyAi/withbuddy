package com.withbuddy.admin.metrics.repository;

import com.withbuddy.admin.metrics.entity.NoResultQuestionPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface NoResultQuestionPatternRepository extends JpaRepository<NoResultQuestionPattern, Long> {

    Optional<NoResultQuestionPattern> findByCompanyCodeAndAnalysisDate(String companyCode, LocalDate analysisDate);

    Optional<NoResultQuestionPattern> findFirstByCompanyCodeOrderByAnalysisDateDesc(String companyCode);
}
