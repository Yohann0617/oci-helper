package com.yohann.ocihelper.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.utils
 * @className: SQLiteHelper
 * @author: Yohann
 * @date: 2025/3/14 23:44
 */
@Component
@Slf4j
public class SQLiteHelper {

    @Value("${spring.datasource.url}")
    private String DB_URL;

    public void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        String checkColumnSql = "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?";
        String alterTableSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(checkColumnSql)) {

            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(alterTableSql);
//                        log.info("Table [{}], Column [{}] added successfully.", tableName, columnName);
                    }
                } else {
//                    log.info("Table [{}], Column [{}] already exists.", tableName, columnName);
                }
            }
        } catch (SQLException e) {
            log.error("更新数据库表结构失败，无法新增列：[{}]", columnName, e);
        }
    }

//    public static void main(String[] args) {
//        addColumnIfNotExists("oci_user", "tenant_name", "VARCHAR(64) NULL");
//    }
}
