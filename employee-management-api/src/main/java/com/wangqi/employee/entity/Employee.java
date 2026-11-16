package com.wangqi.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Employee entity mapped to the {@code employees} table.
 *
 * <p>Bean Validation annotations are declared on the entity as a last line of
 * defence: the API validates its DTOs, but a row can also be written by a
 * scheduled job or an admin script, and Hibernate triggers these constraints on
 * flush for every path.
 *
 * @author wangqi
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a well-formed email address")
    @Size(max = 120, message = "Email must not exceed 120 characters")
    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    @Size(max = 100, message = "Job title must not exceed 100 characters")
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @NotNull(message = "Salary is required")
    @DecimalMin(value = "0.0", message = "Salary must be greater than or equal to 0")
    @Column(name = "salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal salary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
