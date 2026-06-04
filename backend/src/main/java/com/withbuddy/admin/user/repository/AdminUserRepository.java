package com.withbuddy.admin.user.repository;

import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

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
              and u.role in :roles
              and (:department is null or lower(u.department) like lower(concat('%', :department, '%')))
              and (:teamName is null or lower(u.teamName) like lower(concat('%', :teamName, '%')))
            order by
              case when :sortBy is null then
                case
                  when u.accountStatus in :activeStatuses then 0
                  when u.accountStatus in :readOnlyStatuses then 1
                  when u.accountStatus in :inactiveStatuses then 2
                  else 3
                end
              end asc,
              case when :sortBy is null then u.employeeNumber end asc,
              case when :sortBy = 'name' and :sortDirection = 'asc' then u.name end asc,
              case when :sortBy = 'name' and :sortDirection = 'desc' then u.name end desc,
              case when :sortBy = 'employeeNumber' and :sortDirection = 'asc' then u.employeeNumber end asc,
              case when :sortBy = 'employeeNumber' and :sortDirection = 'desc' then u.employeeNumber end desc,
              case when :sortBy = 'hireDate' and :sortDirection = 'asc' then u.hireDate end asc,
              case when :sortBy = 'hireDate' and :sortDirection = 'desc' then u.hireDate end desc
            """)
    Page<User> searchUsers(
            @Param("companyCode") String companyCode,
            @Param("roles") Collection<UserRole> roles,
            @Param("activeStatuses") Collection<UserAccountStatus> activeStatuses,
            @Param("readOnlyStatuses") Collection<UserAccountStatus> readOnlyStatuses,
            @Param("inactiveStatuses") Collection<UserAccountStatus> inactiveStatuses,
            @Param("department") String department,
            @Param("teamName") String teamName,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            Pageable pageable
    );
}
