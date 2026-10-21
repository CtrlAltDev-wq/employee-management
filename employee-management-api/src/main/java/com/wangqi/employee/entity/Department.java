package com.wangqi.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Department entity. The {@code employees} association is mapped as LAZY on
 * purpose: loading it eagerly would silently turn every department query into
 * a join, while relying on lazy loading alone produces the classic N+1 problem
 * when a collection of departments is listed. Callers must opt in explicitly
 * through {@code DepartmentRepository#findAllWithEmployees()}.
 *
 * @author wangqi
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "department")
    @Builder.Default
    private List<Employee> employees = new ArrayList<>();
}
