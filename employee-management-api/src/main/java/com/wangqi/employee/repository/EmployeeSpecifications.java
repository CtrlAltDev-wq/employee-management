package com.wangqi.employee.repository;

import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.entity.Employee;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable JPA Specifications for employee search.
 *
 * <p>Building predicates from the criteria object keeps filtering in the
 * database instead of loading every row and discarding most of them in memory.
 * Absent filters contribute no predicate at all.
 *
 * @author wangqi
 */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(criteria.firstName())) {
                predicates.add(cb.like(cb.lower(root.get("firstName")), contains(criteria.firstName())));
            }
            if (hasText(criteria.lastName())) {
                predicates.add(cb.like(cb.lower(root.get("lastName")), contains(criteria.lastName())));
            }
            if (hasText(criteria.email())) {
                predicates.add(cb.like(cb.lower(root.get("email")), contains(criteria.email())));
            }
            if (hasText(criteria.jobTitle())) {
                predicates.add(cb.like(cb.lower(root.get("jobTitle")), contains(criteria.jobTitle())));
            }
            if (criteria.departmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), criteria.departmentId()));
            }
            if (criteria.minSalary() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("salary"), criteria.minSalary()));
            }
            if (criteria.maxSalary() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("salary"), criteria.maxSalary()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
