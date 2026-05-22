package com.musicplayer.backend;

import javazoom.jl.decoder.*;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.io.*;
import java.sql.*;
import java.util.*;

public class AudioPlayer {

    private static Thread playerThread;
    private static SourceDataLine sourceLine;
    private static volatile boolean stopRequested = false;
    private static volatile boolean pauseRequested = false;
    private static final Object pauseLock = new Object();

    private static String currentPath = "";
    private static volatile float currentVolume = 0.8f; // 0.0 to 1.0
    private static Runnable onSongFinishedCallback;

    public static void setOnSongFinishedCallback(Runnable callback) {
        onSongFinishedCallback = callback;
    }

    // Called from GUI with 0-100
    public static void setVolume(int volume) {
        currentVolume = volume / 100f;
        applyVolume();
    }

    private static void applyVolume() {
        if (sourceLine != null && sourceLine.isOpen()) {
            if (sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = currentVolume == 0 ? gain.getMinimum()
                         : (float)(Math.log10(currentVolume) * 20.0);
                dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                gain.setValue(dB);
            }
        }
    }

    public static void playSong(String songTitle) {
        stopSong();
        currentPath = getPathFromDB(songTitle);
        if (currentPath.isEmpty()) {
            System.err.println("ERROR: song not found in database");
            return;
        }
        stopRequested = false;
        pauseRequested = false;
        startPlayerThread(true);
    }

    public static void pauseSong() {
        pauseRequested = true;
    }

    public static void resumeSong() {
        if (pauseRequested) {
            pauseRequested = false;
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    public static void stopSong() {
        stopRequested = true;
        pauseRequested = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (sourceLine != null) {
            sourceLine.stop();
            sourceLine.flush();
            sourceLine.close();
            sourceLine = null;
        }
        if (playerThread != null) {
            playerThread.interrupt();
            try { playerThread.join(500); } catch (InterruptedException ignored) {}
            playerThread = null;
        }
    }

    private static void startPlayerThread(boolean fromStart) {
        playerThread = new Thread(() -> {
            try {
                String windowsPath = currentPath.replace("/mnt/HDD1TB", "X:").replace("/", "\\");
                FileInputStream fis = new FileInputStream(windowsPath);
                BufferedInputStream bis = new BufferedInputStream(fis);

                Bitstream bitstream = new Bitstream(bis);
                Decoder decoder = new Decoder();

                AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceLine.open(format);
                sourceLine.start();
                applyVolume(); // apply current volume immediately

                Header header;
                while (!stopRequested && (header = bitstream.readFrame()) != null) {
                    // Handle pause
                    synchronized (pauseLock) {
                        while (pauseRequested && !stopRequested) {
                            try { pauseLock.wait(); } catch (InterruptedException e) { break; }
                        }
                    }
                    if (stopRequested) break;

                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] samples = output.getBuffer();
                    int len = output.getBufferLength();

                    // Apply software volume scaling as backup
                    byte[] pcm = new byte[len * 2];
                    for (int i = 0; i < len; i++) {
                        short scaled = (short)(samples[i] * currentVolume);
                        pcm[i * 2]     = (byte)(scaled & 0xFF);
                        pcm[i * 2 + 1] = (byte)((scaled >> 8) & 0xFF);
                    }

                    sourceLine.write(pcm, 0, pcm.length);
                    bitstream.closeFrame();
                }

                sourceLine.drain();
                sourceLine.close();
                bitstream.close();

                if (!stopRequested && onSongFinishedCallback != null) {
                    SwingUtilities.invokeLater(onSongFinishedCallback);
                }

            } catch (Exception e) {
                System.err.println("Playback error: " + e.getMessage());
            }
        });
        playerThread.start();
    }

    // ── DB methods unchanged below ──

    private static String getPathFromDB(String songTitle) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT FILE_PATH FROM SONGS WHERE TITLE = ?")) {
            stmt.setString(1, songTitle);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("FILE_PATH");
        } catch (SQLException e) {
            System.err.println("DB ERROR: " + e.getMessage());
        }
        return "";
    }

    public static String[] getPlaylist() {
        ArrayList<String> songs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT TITLE FROM SONGS ORDER BY TITLE");
            while (rs.next()) songs.add(rs.getString("TITLE"));
        } catch (SQLException e) {
            System.err.println("ERROR loading playlist: " + e.getMessage());
        }
        return songs.toArray(new String[0]);
    }

    public static String[] getArtists() {
        ArrayList<String> artists = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT NAME FROM ARTISTS ORDER BY NAME");
            while (rs.next()) artists.add(rs.getString("NAME"));
        } catch (SQLException e) {
            System.err.println("ERROR loading artists: " + e.getMessage());
        }
        return artists.toArray(new String[0]);
    }

    public static String[] getAlbumsForArtist(String artistName) {
        ArrayList<String> albums = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT DISTINCT A.TITLE FROM ALBUMS A " +
                "JOIN ARTISTS AR ON A.ARTIST_ID = AR.ID " +
                "WHERE AR.NAME = ? ORDER BY A.TITLE")) {
            stmt.setString(1, artistName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) albums.add(rs.getString("TITLE"));
        } catch (SQLException e) {
            System.err.println("ERROR loading albums: " + e.getMessage());
        }
        return albums.toArray(new String[0]);
    }

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
            while (rs.next()) songs.add(rs.getString("TITLE"));
        } catch (SQLException e) {
            System.err.println("ERROR loading songs: " + e.getMessage());
        }
        return songs.toArray(new String[0]);
    }

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