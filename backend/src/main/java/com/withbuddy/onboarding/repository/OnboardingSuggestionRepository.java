package com.withbuddy.onboarding.repository;

import com.withbuddy.onboarding.entity.OnboardingSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingSuggestionRepository extends JpaRepository<OnboardingSuggestion, Long> {
}
