package com.withbuddy.user.entity;

import com.withbuddy.company.entity.Company;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_users_company_employee",
                        columnNames = {"company_code", "employee_number"}
                )
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_code", referencedColumnName = "company_code", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "employee_number", nullable = false, length = 20)
    private String employeeNumber;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(
            Company company,
            String name,
            String employeeNumber,
            LocalDate hireDate,
            UserRole role
    ) {
        this.company = company;
        this.name = name;
        this.employeeNumber = employeeNumber;
        this.hireDate = hireDate;
        this.role = role == null ? UserRole.USER : role;
    }

    public static User createUser(
            Company company,
            String name,
            String employeeNumber,
            LocalDate hireDate
    ) {
        return User.builder()
                .company(company)
                .name(name)
                .employeeNumber(employeeNumber)
                .hireDate(hireDate)
                .role(UserRole.USER)
                .build();
    }
}