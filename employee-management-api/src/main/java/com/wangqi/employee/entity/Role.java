package com.wangqi.employee.entity;

/**
 * Roles understood by Spring Security. They are stored as {@code ROLE_*}
 * strings so that URL-based and method-based rules can use {@code hasRole}.
 *
 * @author wangqi
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
