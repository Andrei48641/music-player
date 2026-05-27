package com.musicplayer.backend;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.sql.*;

public class MusicScanner {

    public static void startScan() {
        File root = new File("X:/10_MUSIC/ALBUMS/");

        if (!root.exists()) {
            System.err.println("ERROR root folder doesn't exist (check tailscale/database) " + root.getAbsolutePath());
            return;
        }

        System.out.println("Starting full scan: " + root.getAbsolutePath());
        scanFolder(root);
        System.out.println("Scan complete!");
    }

    public static void scanFolder(File folder) {
        File[] items = folder.listFiles();
        if (items == null)
            return;

        for (File item : items) {
            if (item.isDirectory()) {
                scanFolder(item);
            } else if (item.getName().endsWith(".mp3")
                    || (item.getName().endsWith(".wav"))) {
                saveToDatabase(item);
            }
        }
    }

    public static void saveToDatabase(File file) {
        try (Connection connectionObj = DatabaseManager.getConnection()) {
            Statement statement = connectionObj.createStatement();

            String linuxPath = file.getAbsolutePath().replace("X:", "/mnt/HDD1TB").replace("\\", "/");

            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            if (tag == null) {
                System.err.println("Skipping file with no metadata: " + file.getName());
                return;
            }

            String artist = safeTag(tag.getFirst(FieldKey.ARTIST));
            String album = safeTag(tag.getFirst(FieldKey.ALBUM));
            String title = safeTag(tag.getFirst(FieldKey.TITLE));
            String year = safeTag(tag.getFirst(FieldKey.YEAR));
            AudioPlayer.cacheSongYear(title, year);

            ResultSet rs = statement
                    .executeQuery("SELECT 1 FROM SONGS WHERE FILE_PATH = '" + linuxPath.replace("'", "''") + "'");
            if (rs.next()) {
                return;
            }

            int artistId = getOrCreateArtist(statement, artist.replace("'", "''"));
            int albumId = getOrCreateAlbum(statement, album.replace("'", "''"), artistId);

            statement.execute("INSERT INTO SONGS (TITLE, ALBUM_ID, FILE_PATH) VALUES ('"
                    + title.replace("'", "''") + "', " + albumId + ", '"
                    + linuxPath.replace("'", "''") + "')");

            System.out.println("debug CHECKED " + tag.getFirst(FieldKey.ARTIST) + " - " + tag.getFirst(FieldKey.TITLE));

        } catch (Exception e) {
            System.err.println("error compiling " + file.getName() + ": " + e.getMessage());
        }
    }

    public static int getOrCreateArtist(Statement statement, String name) throws SQLException {
        statement.execute("MERGE INTO ARTISTS (name) KEY(name) VALUES ('" + name + "')");
        ResultSet rs = statement.executeQuery("SELECT ID FROM ARTISTS WHERE name = '" + name + "'");
        rs.next();
        return rs.getInt("ID");
    }

    public static int getOrCreateAlbum(Statement statement, String title, int artistId) throws SQLException {
        statement.execute("MERGE INTO ALBUMS (TITLE, ARTIST_ID) KEY(TITLE, ARTIST_ID) VALUES ('" + title + "', "
                + artistId + ")");
        ResultSet rs = statement
                .executeQuery("SELECT ID FROM ALBUMS WHERE TITLE = '" + title + "' AND ARTIST_ID = " + artistId);
        rs.next();
        return rs.getInt("ID");
    }

    private static String safeTag(String value) {
        return value == null ? "" : value;
    }
}