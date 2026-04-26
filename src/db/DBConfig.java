package db;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class DBConfig {
    private DBConfig() {
    }

    private static final Properties FILE_PROPS = loadFileProperties();

    private static Properties loadFileProperties() {
        Properties props = new Properties();
        String[] candidates = { "src/database/db.properties", "database/db.properties" };
        for (String candidate : candidates) {
            try (FileInputStream fis = new FileInputStream(candidate)) {
                props.load(fis);
                return props;
            } catch (IOException ignored) {
                // Try next location.
            }
        }
        return props;
    }

    private static String readConfig(String envKey, String defaultValue) {
        String value = System.getenv(envKey);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        // Allow IDE run configuration via -D flags.
        value = System.getProperty(envKey);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }

        value = FILE_PROPS.getProperty(envKey);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return defaultValue;
    }

    public static final String URL = readConfig("EVENT_DB_URL",
            "jdbc:mysql://localhost:3306/campus_event_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");

    public static final String USER = readConfig("EVENT_DB_USER", "root");
    public static final String PASSWORD = readConfig("EVENT_DB_PASSWORD", "");
}
