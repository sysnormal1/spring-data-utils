package com.sysnormal.commons.spring.spring_data_utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MysqlDatabaseUtils extends DatabaseUtils{

    public static final int MAX_FOREIGN_KEY_NAME_LENGTH = 64;

    @Override
    public boolean indexExists(Connection conn, String tableName, String constraintName) throws SQLException {
        String sql = """
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = ?
          AND index_name = ?
    """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, constraintName);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean foreignKeyExists(Connection conn,String tableName,String fkName) throws SQLException {

        String sql = """
        SELECT 1
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
         AND tc.table_schema   = kcu.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_schema = DATABASE()
          AND tc.table_name   = ?
          AND tc.constraint_name = ?
    """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, fkName);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
