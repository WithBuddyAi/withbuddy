package com.withbuddy.storage.repository;

import com.withbuddy.storage.entity.DocumentBackupJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentBackupJobRepository extends JpaRepository<DocumentBackupJob, Long> {
}

