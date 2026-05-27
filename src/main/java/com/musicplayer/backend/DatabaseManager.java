package com.musicplayer.backend;

import java.sql.*;

public class DatabaseManager {

    private static final String JDBC_URL = "jdbc:h2:tcp://100.82.60.98//mnt/HDD1TB/H2_database/musicdb";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, "sa", "");
    }

    // creates playlist tables if they don't exist
    public static void initPlaylistTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS PLAYLISTS (" +
                            "ID INT AUTO_INCREMENT PRIMARY KEY, " +
                            "NAME VARCHAR(255) NOT NULL)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS PLAYLIST_SONGS (" +
                            "ID INT AUTO_INCREMENT PRIMARY KEY, " +
                            "PLAYLIST_ID INT NOT NULL, " +
                            "SONG_ID INT NOT NULL, " +
                            "FOREIGN KEY (PLAYLIST_ID) REFERENCES PLAYLISTS(ID) ON DELETE CASCADE, " +
                            "FOREIGN KEY (SONG_ID) REFERENCES SONGS(ID) ON DELETE CASCADE)");
            System.out.println("Playlist tables ready.");
        } catch (SQLException e) {
            System.err.println("ERROR creating playlist tables: " + e.getMessage());
        }
    }

}