package com.musicplayer.backend;

import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import javax.swing.SwingUtilities; // Crucial for the Auto-Play callback!
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.sql.*;
import java.util.ArrayList;

public class AudioPlayer {

    private static AdvancedPlayer mp3Player;
    private static Thread playerThread;

    private static String currentPath = "";
    private static int pausedFrame = 0;
    private static boolean isPaused = false;
    
    // Auto-play and pause logic variables
    private static boolean manuallyStopped = false;
    private static Runnable onSongFinishedCallback;

    // Method for the GUI to hand over its callback instructions
    public static void setOnSongFinishedCallback(Runnable callback) {
        onSongFinishedCallback = callback;
    }

    public static void playSong(String songTitle) {
        stopSong(); // Kick out the old song before starting a new one
        pausedFrame = 0;
        isPaused = false;
        manuallyStopped = false; // Reset the flag when a new song starts

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
            manuallyStopped = true; // Prevents the auto-play bug!
            
            // Safety net around JLayer's cranky stop method
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
                // If the song was completely finished, restart it from the beginning!
                pausedFrame = 0;
                manuallyStopped = false;
                playFromFrame(0);
            }
        }
    }

    public static void stopSong() {
        isPaused = false;
        pausedFrame = 0;
        manuallyStopped = true; // Prevents auto playing when we press stop
        
        if (mp3Player != null) {
            try {
                mp3Player.stop(); // safety net
            } catch (Exception e) {
                
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
                        // Track which frame we stopped at for pause/resume
                        pausedFrame = startFrame + e.getFrame();
                        System.out.println("Stopped at frame: " + pausedFrame);
                        
                        
                        if (!manuallyStopped) {
                            mp3Player = null; // stop player
                            pausedFrame = 0;  // reset
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
             Statement statement = conn.createStatement()) {

            ResultSet rs = statement.executeQuery(
                "SELECT FILE_PATH FROM SONGS WHERE TITLE = '" + songTitle + "'"
            );

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
}