package com.withbuddy.admin.user.repository;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {

    boolean existsByCompany_CompanyCodeAndEmployeeNumber(
            String companyCode,
            String employeeNumber
    );

    @Query("""
            select u
            from User u
            where u.company.companyCode = :companyCode
              and u.role = :role
              and (:department is null or lower(u.department) like lower(concat('%', :department, '%')))
              and (:teamName is null or lower(u.teamName) like lower(concat('%', :teamName, '%')))
            """)
    Page<User> searchUsers(
            @Param("companyCode") String companyCode,
            @Param("role") UserRole role,
            @Param("department") String department,
            @Param("teamName") String teamName,
            Pageable pageable
    );
}
