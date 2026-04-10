package com.withbuddy.auth.repository;

import com.withbuddy.user.entity.User;
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

}
