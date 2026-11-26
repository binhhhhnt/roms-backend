package com.roms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates daily atomic session codes in format S-YYYYMMDD-XXXX
 * using PostgreSQL UPSERT on daily_sequences to prevent race conditions.
 */
@Component
@RequiredArgsConstructor
public class SessionCodeGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public String nextSessionCode() {
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        String seqKey = "SESSION-" + dateStr;
        Long seq = jdbcTemplate.queryForObject(
                "INSERT INTO daily_sequences (seq_key, last_value) VALUES (?, 1) " +
                "ON CONFLICT (seq_key) DO UPDATE SET last_value = daily_sequences.last_value + 1 " +
                "RETURNING last_value",
                Long.class,
                seqKey
        );
        long value = (seq != null) ? seq : 1L;
        return String.format("S-%s-%04d", dateStr, value);
    }
}
