package com.withbuddy.admin.organization.repository;

import com.withbuddy.account.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

@org.springframework.stereotype.Repository
public interface AdminOrganizationOptionRepository extends Repository<User, Long> {

    @Query(value = """
            SELECT
                department AS department,
                team_name AS teamName
            FROM company_organization_units
            WHERE company_code = :companyCode
            ORDER BY department ASC, team_name ASC
            """, nativeQuery = true)
    List<OrganizationOptionProjection> findOrganizationOptions(
            @Param("companyCode") String companyCode
    );

    interface OrganizationOptionProjection {
        String getDepartment();

        String getTeamName();
    }
}
