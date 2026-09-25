package com.portfolio.tracker.tutorial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TutorialStateRepository extends JpaRepository<TutorialState, UUID> {
}
