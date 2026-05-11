package com.withbuddy.account.auth.repository;

import com.withbuddy.account.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByCompany_CompanyCodeAndNameAndEmployeeNumber(
            String companyCode,
            String name,
            String employeeNumber
    );

    boolean existsByCompany_CompanyCodeAndEmployeeNumber(
            String companyCode,
            String employeeNumber
    );
}
