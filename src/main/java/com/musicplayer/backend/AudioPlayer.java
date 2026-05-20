package com.musicplayer.backend;

import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import javax.swing.SwingUtilities;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.sql.*;
import java.util.*;

public class AudioPlayer {

    private static AdvancedPlayer mp3Player;
    private static Thread playerThread;

    private static String currentPath = "";
    private static int pausedFrame = 0;
    private static boolean isPaused = false;
    
    private static boolean manuallyStopped = false;
    private static Runnable onSongFinishedCallback;

    public static void setOnSongFinishedCallback(Runnable callback) {
        onSongFinishedCallback = callback;
    }

    public static void playSong(String songTitle) {
        stopSong();
        pausedFrame = 0;
        isPaused = false;
        manuallyStopped = false;

        currentPath = getPathFromDB(songTitle);
        if (currentPath.isEmpty()) {
            System.err.println("ERROR song not found in database");
            return;
        }

        playFromFrame(pausedFrame);
    }

    public static void pauseSong() {
        if (mp3Player != null && !isPaused) {
            isPaused = true;
            manuallyStopped = true;
            
            try {
                mp3Player.stop(); 
            } catch (Exception e) {
                System.out.println("Safely ignored JLayer error: " + e.getMessage());
            }
            
            System.out.println("Paused at frame: " + pausedFrame);
        }
    }

    public static void resumeSong() {
        if (!currentPath.isEmpty()) {
            if (isPaused) {
                isPaused = false;
                manuallyStopped = false; 
                playFromFrame(pausedFrame);
            } else if (mp3Player == null) {
                pausedFrame = 0;
                manuallyStopped = false;
                playFromFrame(0);
            }
        }
    }

    public static void stopSong() {
        isPaused = false;
        pausedFrame = 0;
        manuallyStopped = true;
        
        if (mp3Player != null) {
            try {
                mp3Player.stop();
            } catch (Exception e) {
                // Ignore
            }
            mp3Player = null;
        }
        if (playerThread != null) {
            playerThread.interrupt();
            playerThread = null;
        }
        System.out.println("Music stopped!");
    }

    private static void playFromFrame(int startFrame) {
        playerThread = new Thread(() -> {
            try {
                String windowsPath = currentPath.replace("/mnt/HDD1TB", "X:").replace("/", "\\");
                FileInputStream fis = new FileInputStream(windowsPath);
                BufferedInputStream bis = new BufferedInputStream(fis);

                mp3Player = new AdvancedPlayer(bis);
                mp3Player.setPlayBackListener(new PlaybackListener() {
                    @Override
                    public void playbackStarted(PlaybackEvent e) {
                        System.out.println("NOW PLAYING from frame: " + startFrame);
                    }

                    @Override
                    public void playbackFinished(PlaybackEvent e) {
                        pausedFrame = startFrame + e.getFrame();
                        System.out.println("Stopped at frame: " + pausedFrame);
                        
                        if (!manuallyStopped) {
                            mp3Player = null;
                            pausedFrame = 0;
                            isPaused = false;
                            
                            if (onSongFinishedCallback != null) {
                                SwingUtilities.invokeLater(onSongFinishedCallback);
                            }
                        }
                    }
                });

                mp3Player.play(startFrame, Integer.MAX_VALUE);

            } catch (Exception e) {
                System.err.println("ERROR playing: " + e.getMessage());
            }
        });
        playerThread.start();
    }

    private static String getPathFromDB(String songTitle) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT FILE_PATH FROM SONGS WHERE TITLE = ?")) {
            stmt.setString(1, songTitle);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("FILE_PATH");
            }
        } catch (SQLException e) {
            System.err.println("database ERROR " + e.getMessage());
        }
        return "";
    }

    public static String[] getPlaylist() {
        ArrayList<String> songs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             Statement statement = conn.createStatement()) {

            ResultSet rs = statement.executeQuery(
                "SELECT TITLE FROM SONGS ORDER BY TITLE"
            );

            while (rs.next()) {
                songs.add(rs.getString("TITLE"));
            }

        } catch (SQLException e) {
            System.err.println("ERROR loading playlist: " + e.getMessage());
        }

        return songs.toArray(new String[0]);
    }

    /**
     * Get all artists from the database
     */
    public static String[] getArtists() {
        ArrayList<String> artists = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             Statement statement = conn.createStatement()) {

            ResultSet rs = statement.executeQuery(
                "SELECT NAME FROM ARTISTS ORDER BY NAME"
            );

            while (rs.next()) {
                artists.add(rs.getString("NAME"));
            }

        } catch (SQLException e) {
            System.err.println("ERROR loading artists: " + e.getMessage());
        }

        return artists.toArray(new String[0]);
    }

    /**
     * Get all albums for a specific artist
     */
    public static String[] getAlbumsForArtist(String artistName) {
        ArrayList<String> albums = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT DISTINCT A.TITLE FROM ALBUMS A " +
                "JOIN ARTISTS AR ON A.ARTIST_ID = AR.ID " +
                "WHERE AR.NAME = ? ORDER BY A.TITLE")) {
            
            stmt.setString(1, artistName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                albums.add(rs.getString("TITLE"));
            }

        } catch (SQLException e) {
            System.err.println("ERROR loading albums: " + e.getMessage());
        }

        return albums.toArray(new String[0]);
    }

    /**
     * Get all songs for a specific album and artist
     */
    public static String[] getSongsForAlbum(String artistName, String albumTitle) {
        ArrayList<String> songs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT S.TITLE FROM SONGS S " +
                "JOIN ALBUMS A ON S.ALBUM_ID = A.ID " +
                "JOIN ARTISTS AR ON A.ARTIST_ID = AR.ID " +
                "WHERE AR.NAME = ? AND A.TITLE = ? ORDER BY S.TITLE")) {
            
            stmt.setString(1, artistName);
            stmt.setString(2, albumTitle);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                songs.add(rs.getString("TITLE"));
            }

        } catch (SQLException e) {
            System.err.println("ERROR loading songs: " + e.getMessage());
        }

        return songs.toArray(new String[0]);
    }

    /**
     * Get song info including file path
     */
    public static Map<String, String> getSongInfo(String songTitle) {
        Map<String, String> info = new HashMap<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT S.TITLE, S.FILE_PATH, A.TITLE AS ALBUM, AR.NAME AS ARTIST " +
                "FROM SONGS S " +
                "JOIN ALBUMS A ON S.ALBUM_ID = A.ID " +
                "JOIN ARTISTS AR ON A.ARTIST_ID = AR.ID " +
                "WHERE S.TITLE = ?")) {
            
            stmt.setString(1, songTitle);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                info.put("TITLE", rs.getString("TITLE"));
                info.put("FILE_PATH", rs.getString("FILE_PATH"));
                info.put("ALBUM", rs.getString("ALBUM"));
                info.put("ARTIST", rs.getString("ARTIST"));
            }

        } catch (SQLException e) {
            System.err.println("ERROR loading song info: " + e.getMessage());
        }

        return info;
    }
}