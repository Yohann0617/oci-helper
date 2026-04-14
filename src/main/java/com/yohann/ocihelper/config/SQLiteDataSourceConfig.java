package com.yohann.ocihelper.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

/**
 * SQLite DataSource configuration.
 *
 * <p>SQLite uses file-level locking. Concurrent writes from multiple threads will
 * cause {@code SQLITE_BUSY} errors unless serialized. The simplest and most
 * compatible solution is to limit HikariCP to a single physical connection
 * ({@code maximumPoolSize=1}), which forces all operations to queue up and execute
 * one at a time — no WAL mode required, no extra sidecar files (.db-shm/.db-wal).</p>
 *
 * <p>A {@code busy_timeout} is still set as a safety net in case an external process
 * (e.g. the install script's sqlite3 command) briefly holds the file lock.</p>
 *
 * @author Yohann
 */
@Slf4j
@Configuration
public class SQLiteDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        SQLiteDataSource sqLiteDataSource = new SQLiteDataSource();
        sqLiteDataSource.setUrl(jdbcUrl);
        // Wait up to 5 s if an external process (e.g. install script) holds the lock
        sqLiteDataSource.setBusyTimeout(5000);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDataSource(sqLiteDataSource);
        // Single connection serializes all reads and writes at the pool level,
        // completely preventing SQLITE_BUSY without WAL or any locking overhead.
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
        hikariConfig.setPoolName("sqlite-hikari-pool");
        hikariConfig.setConnectionTestQuery("SELECT 1");

        log.info("SQLite DataSource initialized: pool-size=1 (serialized), busy_timeout=5s");
        return new HikariDataSource(hikariConfig);
    }
}
