package com.musicplayer.backend;

import javazoom.jl.decoder.*;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.sql.*;
import java.util.*;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;

public class AudioPlayer {

    private static SourceDataLine sourceLine;
    private static Thread playerThread;

    private static String currentPath = "";
    private static int pausedFrame = 0;
    private static boolean isPaused = false;

    private static boolean manuallyStopped = false;
    private static Runnable onSongFinishedCallback;

    // volume field
    private static volatile float currentVolume = 0.8f;

    // progress tracking
    private static volatile int framesPlayed = 0;
    private static volatile int totalSeconds = 0;

    public static void setOnSongFinishedCallback(Runnable callback) {
        onSongFinishedCallback = callback;
    }

    // called from GUI with 0-100
    public static void setVolume(int volume) {
        currentVolume = volume / 100f;
        // apply to live line if playing
        if (sourceLine != null && sourceLine.isOpen()
                && sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = currentVolume == 0 ? gain.getMinimum()
                    : (float) (Math.log10(currentVolume) * 20.0);
            dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
            gain.setValue(dB);
        }
    }

    public static int getCurrentSeconds() {
        return (int) (framesPlayed * 1152.0 / 44100.0);
    }

    public static int getTotalSeconds() {
        return totalSeconds;
    }

    public static void seekTo(int seconds) {
        if (currentPath.isEmpty())
            return;
        int targetFrame = (int) (seconds * 44100.0 / 1152.0);
        manuallyStopped = true;
        if (sourceLine != null) {
            sourceLine.stop();
            sourceLine.flush();
            sourceLine.close();
            sourceLine = null;
        }
        if (playerThread != null) {
            playerThread.interrupt();
            playerThread = null;
        }
        manuallyStopped = false;
        isPaused = false;
        framesPlayed = targetFrame;
        pausedFrame = targetFrame;
        playFromFrame(targetFrame);
    }

    //
    public static void playSong(String songTitle) {
        stopSong();
        pausedFrame = 0;
        framesPlayed = 0;
        isPaused = false;
        manuallyStopped = false;

        currentPath = getPathFromDB(songTitle);
        if (currentPath.isEmpty()) {
            System.err.println("ERROR song not found in database");
            return;
        }

        // get total duration
        try {
            String wp = currentPath.replace("/mnt/HDD1TB", "X:").replace("/", "\\");
            AudioFile af = AudioFileIO.read(new java.io.File(wp));
            totalSeconds = af.getAudioHeader().getTrackLength();
        } catch (Exception e) {
            totalSeconds = 0;
        }

        playFromFrame(pausedFrame);
    }

    //
    public static void pauseSong() {
        if (!isPaused) {
            isPaused = true;
            manuallyStopped = true;
            if (sourceLine != null) {
                sourceLine.stop();
            }
            System.out.println("Paused at frame: " + pausedFrame);
        }
    }

    //
    public static void resumeSong() {
        if (!currentPath.isEmpty()) {
            if (isPaused) {
                isPaused = false;
                manuallyStopped = false;
                playFromFrame(pausedFrame);
            } else if (sourceLine == null) {
                pausedFrame = 0;
                manuallyStopped = false;
                playFromFrame(0);
            }
        }
    }

    //
    public static void stopSong() {
        isPaused = false;
        pausedFrame = 0;
        manuallyStopped = true;

        if (sourceLine != null) {
            sourceLine.stop();
            sourceLine.flush();
            sourceLine.close();
            sourceLine = null;
        }
        if (playerThread != null) {
            playerThread.interrupt();
            playerThread = null;
        }
        System.out.println("Music stopped!");
    }

    // Bitstream+Decoder+SourceDataLine
    private static void playFromFrame(int startFrame) {
        playerThread = new Thread(() -> {
            try {
                String windowsPath = currentPath.replace("/mnt/HDD1TB", "X:").replace("/", "\\");
                FileInputStream fis = new FileInputStream(windowsPath);
                BufferedInputStream bis = new BufferedInputStream(fis);

                Bitstream bitstream = new Bitstream(bis);
                Decoder decoder = new Decoder();

                // skip to startFrame
                for (int i = 0; i < startFrame; i++) {
                    Header h = bitstream.readFrame();
                    if (h == null)
                        break;
                    bitstream.closeFrame();
                }

                AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceLine.open(format, 65536);
                sourceLine.start();

                // apply current volume immediately
                setVolume((int) (currentVolume * 100));

                framesPlayed = startFrame;
                Header header;
                while (!manuallyStopped && (header = bitstream.readFrame()) != null) {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] samples = output.getBuffer();
                    int len = output.getBufferLength();

                    // software volume scaling as fallback
                    byte[] pcm = new byte[len * 2];
                    for (int i = 0; i < len; i++) {
                        short scaled = (short) (samples[i] * currentVolume);
                        pcm[i * 2] = (byte) (scaled & 0xFF);
                        pcm[i * 2 + 1] = (byte) ((scaled >> 8) & 0xFF);
                    }

                    sourceLine.write(pcm, 0, pcm.length);
                    bitstream.closeFrame();
                    framesPlayed++;
                }

                pausedFrame = framesPlayed;
                sourceLine.drain();
                sourceLine.close();
                bitstream.close();

                if (!manuallyStopped) {
                    pausedFrame = 0;
                    isPaused = false;
                    if (onSongFinishedCallback != null) {
                        SwingUtilities.invokeLater(onSongFinishedCallback);
                    }
                }

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
            if (rs.next())
                return rs.getString("FILE_PATH");
        } catch (SQLException e) {
            System.err.println("database ERROR " + e.getMessage());
        }
        return "";
    }

    public static String[] getPlaylist() {
        ArrayList<String> songs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
                Statement statement = conn.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT TITLE FROM SONGS ORDER BY TITLE");
            while (rs.next())
                songs.add(rs.getString("TITLE"));
        } catch (SQLException e) {
            System.err.println("ERROR loading playlist: " + e.getMessage());
        }
        return songs.toArray(new String[0]);
    }

    public static String[] getArtists() {
        ArrayList<String> artists = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
                Statement statement = conn.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT NAME FROM ARTISTS ORDER BY NAME");
            while (rs.next())
                artists.add(rs.getString("NAME"));
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
            while (rs.next())
                albums.add(rs.getString("TITLE"));
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
            while (rs.next())
                songs.add(rs.getString("TITLE"));
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

    // ── Playlist methods ──

    public static void createPlaylist(String name) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO PLAYLISTS (NAME) VALUES (?)")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ERROR creating playlist: " + e.getMessage());
        }
    }

    public static void deletePlaylist(int playlistId) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM PLAYLISTS WHERE ID = ?")) {
            stmt.setInt(1, playlistId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ERROR deleting playlist: " + e.getMessage());
        }
    }

    public static Map<Integer, String> getPlaylists() {
        Map<Integer, String> playlists = new LinkedHashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT ID, NAME FROM PLAYLISTS ORDER BY NAME");
            while (rs.next()) {
                playlists.put(rs.getInt("ID"), rs.getString("NAME"));
            }
        } catch (SQLException e) {
            System.err.println("ERROR loading playlists: " + e.getMessage());
        }
        return playlists;
    }

    public static void addSongToPlaylist(int playlistId, String songTitle) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement getSong = conn.prepareStatement(
                        "SELECT ID FROM SONGS WHERE TITLE = ?")) {
            getSong.setString(1, songTitle);
            ResultSet rs = getSong.executeQuery();
            if (rs.next()) {
                int songId = rs.getInt("ID");
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO PLAYLIST_SONGS (PLAYLIST_ID, SONG_ID) VALUES (?, ?)");
                insert.setInt(1, playlistId);
                insert.setInt(2, songId);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("ERROR adding song to playlist: " + e.getMessage());
        }
    }

    public static String[] getSongsForPlaylist(int playlistId) {
        ArrayList<String> songs = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT S.TITLE FROM SONGS S " +
                                "JOIN PLAYLIST_SONGS PS ON S.ID = PS.SONG_ID " +
                                "WHERE PS.PLAYLIST_ID = ?")) {
            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next())
                songs.add(rs.getString("TITLE"));
        } catch (SQLException e) {
            System.err.println("ERROR loading playlist songs: " + e.getMessage());
        }
        return songs.toArray(new String[0]);
    }

    public static void removeSongFromPlaylist(int playlistId, String songTitle) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM PLAYLIST_SONGS WHERE PLAYLIST_ID = ? AND SONG_ID = " +
                                "(SELECT ID FROM SONGS WHERE TITLE = ?)")) {
            stmt.setInt(1, playlistId);
            stmt.setString(2, songTitle);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ERROR removing song from playlist: " + e.getMessage());
        }
    }
}