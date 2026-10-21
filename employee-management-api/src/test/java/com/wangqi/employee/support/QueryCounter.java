package com.wangqi.employee.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts every SQL statement Hibernate sends to the database during a test.
 * Registered through the {@code hibernate.session_factory.statement_inspector}
 * property so query counts can be asserted deterministically instead of
 * relying on log inspection.
 *
 * @author wangqi
 */
public class QueryCounter implements StatementInspector {

    private static final AtomicInteger COUNT = new AtomicInteger();

    @Override
    public String inspect(String sql) {
        COUNT.incrementAndGet();
        return sql;
    }

    public static void reset() {
        COUNT.set(0);
    }

    public static int count() {
        return COUNT.get();
    }
}
