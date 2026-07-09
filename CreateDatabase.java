import java.sql.Connection;
import java.sql.Statement;
import com.mysql.cj.jdbc.MysqlDataSource;

public class CreateDatabase {
    public static void main(String[] args) throws Exception {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl("jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME", "root"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS travel_adventure CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("DB_CREATED");
        }
    }
}
