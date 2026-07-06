package com.withbuddy.account.user.repository;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserLifecycleRepository extends JpaRepository<User, Long> {

    List<User> findByRole(UserRole role);
}
