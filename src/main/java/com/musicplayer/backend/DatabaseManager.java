    package com.musicplayer.backend;

    import java.sql.*;

    public class DatabaseManager {

        private static final String JDBC_URL = "jdbc:h2:tcp://100.82.60.98//mnt/HDD1TB/H2_database/musicdb";

        public static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(JDBC_URL, "sa", "");
        }

    }