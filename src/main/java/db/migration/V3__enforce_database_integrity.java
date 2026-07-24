package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class V3__enforce_database_integrity extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        ensureIndex(
                connection,
                "check_ins",
                "idx_checkins_user_landmark",
                true,
                List.of("user_id", "landmark_id")
        );
        ensureIndex(
                connection,
                "check_ins",
                "idx_checkins_user_completed",
                false,
                List.of("user_id", "completed")
        );
        ensureIndex(
                connection,
                "player_progress",
                "idx_progress_user_city",
                true,
                List.of("user_id", "city_id")
        );
        ensureIndex(
                connection,
                "cities",
                "idx_cities_unlock_order",
                true,
                List.of("city_order")
        );
    }

    private void ensureIndex(
            Connection connection,
            String table,
            String indexName,
            boolean unique,
            List<String> columns
    ) throws SQLException {
        List<IndexDefinition> indexes = readIndexes(connection, table);
        if (indexes.stream().anyMatch(index -> index.matches(unique, columns))) {
            return;
        }

        if (indexes.stream().anyMatch(index -> index.name().equalsIgnoreCase(indexName))) {
            throw new FlywayException(
                    "Index " + indexName + " already exists with a different definition"
            );
        }

        String uniqueness = unique ? "UNIQUE " : "";
        String sql = "CREATE " + uniqueness + "INDEX " + indexName
                + " ON " + table + " (" + String.join(", ", columns) + ")";

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<IndexDefinition> readIndexes(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String actualTableName = resolveTableName(connection, table);
        Map<String, MutableIndexDefinition> indexes = new HashMap<>();

        try (ResultSet rows = metadata.getIndexInfo(
                connection.getCatalog(),
                connection.getSchema(),
                actualTableName,
                false,
                false
        )) {
            while (rows.next()) {
                String indexName = rows.getString("INDEX_NAME");
                String columnName = rows.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }

                String key = indexName.toLowerCase(Locale.ROOT);
                MutableIndexDefinition index = indexes.computeIfAbsent(
                        key,
                        ignored -> new MutableIndexDefinition(indexName, !rowsUncheckedBoolean(rows, "NON_UNIQUE"))
                );
                index.columns().add(new IndexedColumn(
                        rows.getShort("ORDINAL_POSITION"),
                        columnName.toLowerCase(Locale.ROOT)
                ));
            }
        }

        return indexes.values().stream()
                .map(MutableIndexDefinition::toImmutable)
                .toList();
    }

    private String resolveTableName(Connection connection, String requestedName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(),
                connection.getSchema(),
                null,
                new String[]{"TABLE"}
        )) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName.equalsIgnoreCase(requestedName)) {
                    return tableName;
                }
            }
        }
        throw new FlywayException("Required table does not exist: " + requestedName);
    }

    private static boolean rowsUncheckedBoolean(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new FlywayException("Unable to read database index metadata", exception);
        }
    }

    private record IndexedColumn(short position, String name) {
    }

    private record IndexDefinition(String name, boolean unique, List<String> columns) {

        private boolean matches(boolean requiredUnique, List<String> requiredColumns) {
            List<String> normalizedColumns = requiredColumns.stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .toList();
            return (!requiredUnique || unique) && columns.equals(normalizedColumns);
        }
    }

    private record MutableIndexDefinition(
            String name,
            boolean unique,
            List<IndexedColumn> columns
    ) {

        private MutableIndexDefinition(String name, boolean unique) {
            this(name, unique, new ArrayList<>());
        }

        private IndexDefinition toImmutable() {
            List<String> orderedColumns = columns.stream()
                    .sorted(Comparator.comparingInt(IndexedColumn::position))
                    .map(IndexedColumn::name)
                    .toList();
            return new IndexDefinition(name, unique, orderedColumns);
        }
    }
}
