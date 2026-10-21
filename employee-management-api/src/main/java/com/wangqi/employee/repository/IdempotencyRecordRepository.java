package com.wangqi.employee.repository;

import com.wangqi.employee.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link IdempotencyRecord}.
 *
 * @author wangqi
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
