package com.wangqi.employee.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.entity.IdempotencyRecord;
import com.wangqi.employee.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Persistence and fingerprinting for idempotent writes.
 *
 * @author wangqi
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public Optional<IdempotencyRecord> find(String key) {
        return idempotencyRecordRepository.findById(key);
    }

    /**
     * SHA-256 of the canonical JSON payload. Comparing fingerprints is what
     * distinguishes a legitimate retry from a key being reused for a different
     * request.
     */
    public String fingerprint(EmployeeRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to fingerprint the request payload", ex);
        }
    }

    public void remember(String key, String requestHash, EmployeeResponse response) {
        try {
            idempotencyRecordRepository.save(IdempotencyRecord.builder()
                    .key(key)
                    .requestHash(requestHash)
                    .responseBody(objectMapper.writeValueAsString(response))
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to store the idempotent response", ex);
        }
    }

    public EmployeeResponse readResponse(IdempotencyRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), EmployeeResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to read the stored idempotent response", ex);
        }
    }
}
