package com.example.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationTest {

    @Test
    void existingSchemaIsBaselinedAndIntegrityConstraintsAreAdded() throws Exception {
        String url = "jdbc:h2:mem:flyway_migration_test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE landmarks (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE cities (id BIGINT PRIMARY KEY, city_order INT)");
            statement.execute("CREATE TABLE check_ins ("
                    + "id BIGINT PRIMARY KEY, user_id BIGINT, landmark_id BIGINT, completed BOOLEAN)");
            statement.execute("CREATE TABLE player_progress ("
                    + "id BIGINT PRIMARY KEY, user_id BIGINT, city_id BIGINT)");
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            assertTrue(hasColumn(connection, "USERS", "VERSION"));
            assertTrue(hasColumn(connection, "PLAYER_PROGRESS", "VERSION"));
            assertTrue(hasIndex(connection, "CHECK_INS", "IDX_CHECKINS_USER_LANDMARK", true));
            assertTrue(hasIndex(connection, "CHECK_INS", "IDX_CHECKINS_USER_COMPLETED", false));
            assertTrue(hasIndex(connection, "PLAYER_PROGRESS", "IDX_PROGRESS_USER_CITY", true));
            assertTrue(hasIndex(connection, "CITIES", "IDX_CITIES_UNLOCK_ORDER", true));

            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO check_ins (id, user_id, landmark_id, completed) "
                        + "VALUES (1, 10, 100, TRUE)");
                assertThrows(SQLException.class, () ->
                        statement.execute("INSERT INTO check_ins (id, user_id, landmark_id, completed) "
                                + "VALUES (2, 10, 100, FALSE)"));

                statement.execute("INSERT INTO player_progress (id, version, user_id, city_id) "
                        + "VALUES (1, 0, 10, 1)");
                assertThrows(SQLException.class, () ->
                        statement.execute("INSERT INTO player_progress (id, version, user_id, city_id) "
                                + "VALUES (2, 0, 10, 1)"));

                statement.execute("INSERT INTO cities (id, city_order) VALUES (1, 1)");
                assertThrows(SQLException.class, () ->
                        statement.execute("INSERT INTO cities (id, city_order) VALUES (2, 1)"));
            }
        }
    }

    private boolean hasColumn(java.sql.Connection connection, String table, String column) throws Exception {
        try (var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private boolean hasIndex(
            java.sql.Connection connection,
            String table,
            String index,
            boolean unique
    ) throws Exception {
        try (var indexes = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))
                        && unique == !indexes.getBoolean("NON_UNIQUE")) {
                    return true;
                }
            }
            return false;
        }
    }
}
