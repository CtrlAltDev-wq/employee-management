package com.wangqi.employee.repository;

import com.wangqi.employee.entity.OnboardingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OnboardingTask}.
 *
 * @author wangqi
 */
@Repository
public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, Long> {

    boolean existsByEmployeeId(Long employeeId);
}
