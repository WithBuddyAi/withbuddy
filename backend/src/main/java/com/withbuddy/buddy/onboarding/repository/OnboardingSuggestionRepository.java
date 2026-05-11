package com.withbuddy.buddy.onboarding.repository;

import com.withbuddy.buddy.onboarding.entity.OnboardingSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OnboardingSuggestionRepository extends JpaRepository<OnboardingSuggestion, Long> {
    Optional<OnboardingSuggestion> findTopByDayOffset(Integer dayOffset);
}
