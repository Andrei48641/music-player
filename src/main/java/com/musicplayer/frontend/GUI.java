package com.musicplayer.frontend;

import javax.swing.*;
import java.awt.*;
import java.util.Random;
import com.musicplayer.backend.AudioPlayer;

public class GUI extends JFrame {

    private JLabel titleLabel;
    private JLabel artistLabel;
    private JButton playPauseBtn;

    private String[] playlist;
    private int currentIndex = -1;
    private boolean isPlaying = false;

    public GUI() {
        setTitle("music player");
        setSize(400, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        playlist = AudioPlayer.getPlaylist();
        //playlist = new String[]{"Sultans of Swing", "Lady Writer", "Six Blade Knife", "Romeo and Juliet", "Walk of Life"};

        // menu Bar
        JMenuBar menuBar = new JMenuBar();

        JMenu songMenu = new JMenu("Exit");
        JMenuItem exitItem = new JMenuItem("Exit Player");
        exitItem.addActionListener(e -> System.exit(0));
        songMenu.add(exitItem);
        menuBar.add(songMenu);

        // scrollable playlist menu
        JMenu playlistMenu = new JMenu("Playlist");
        JPopupMenu popupMenu = playlistMenu.getPopupMenu();
        popupMenu.setLayout(new BorderLayout());

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        for (int i = 0; i < playlist.length; i++) {
            final int index = i;
            JMenuItem songItem = new JMenuItem(playlist[i]);
            songItem.addActionListener(e -> startNewSong(index));
            listPanel.add(songItem);
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(250, 300));
        scrollPane.setBorder(null);
        popupMenu.add(scrollPane, BorderLayout.CENTER);

        menuBar.add(playlistMenu);
        setJMenuBar(menuBar);

        // center panel
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(Color.BLACK);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));

        try {
            Image scaledImg = new ImageIcon("cover.jpg").getImage()
                    .getScaledInstance(200, 200, Image.SCALE_SMOOTH);
            JLabel albumArt = new JLabel(new ImageIcon(scaledImg));
            albumArt.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(albumArt);
        } catch (Exception e) {
            System.out.println("Cover image not found, skipping album art.");
        }

        centerPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        titleLabel = new JLabel("Ready to Play");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);

        artistLabel = new JLabel("Select a song from Playlist");
        artistLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        artistLabel.setForeground(Color.LIGHT_GRAY);
        artistLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(artistLabel);

        add(centerPanel, BorderLayout.CENTER);

        // bottom panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(Color.BLACK);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 40, 20));

        // progress slider
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(Color.BLACK);
        JLabel timeStart = new JLabel("00:00");
        timeStart.setForeground(Color.WHITE);
        JLabel timeEnd = new JLabel("00:00");
        timeEnd.setForeground(Color.WHITE);
        JSlider slider = new JSlider(0, 100, 0);
        slider.setBackground(Color.BLACK);
        progressPanel.add(timeStart, BorderLayout.WEST);
        progressPanel.add(slider, BorderLayout.CENTER);
        progressPanel.add(timeEnd, BorderLayout.EAST);
        bottomPanel.add(progressPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));

           // volume control - top right hover popover
        JPanel topRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        topRightPanel.setBackground(Color.BLACK);

        JSlider volumeSlider = new JSlider(JSlider.VERTICAL, 0, 100, 80);
        volumeSlider.setBackground(new Color(30, 30, 30));
        volumeSlider.setPreferredSize(new Dimension(30, 100));
        volumeSlider.setInverted(true);

        JLabel popupVolLabel = new JLabel("80%");
        popupVolLabel.setForeground(Color.LIGHT_GRAY);
        popupVolLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        popupVolLabel.setHorizontalAlignment(SwingConstants.CENTER);

        volumeSlider.addChangeListener(e ->
            popupVolLabel.setText(volumeSlider.getValue() + "%")
        );

        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new BoxLayout(popupPanel, BoxLayout.Y_AXIS));
        popupPanel.setBackground(new Color(30, 30, 30));
        popupPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        popupPanel.add(volumeSlider);
        popupPanel.add(popupVolLabel);

        JPopupMenu volumePopup = new JPopupMenu();
        volumePopup.setBackground(new Color(30, 30, 30));
        volumePopup.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        volumePopup.add(popupPanel);

        JButton volButton = createIconButton("🔊", 20);
        volButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
               volumePopup.show(volButton, 0, volButton.getHeight() + 5); 
            }
        });

        topRightPanel.add(volButton);
        add(topRightPanel, BorderLayout.NORTH);
        // controls
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        controlsPanel.setBackground(Color.BLACK);

        // shuffle
        JButton shuffleBtn = createIconButton("🔀", 28);
        shuffleBtn.addActionListener(e -> {
            if (playlist.length > 1) {
                Random rand = new Random();
                int pick = currentIndex;
                while (pick == currentIndex) {
                    pick = rand.nextInt(playlist.length);
                }
                startNewSong(pick);
            }
        });

        // previous
        JButton prevBtn = createIconButton("⏮", 28);
        prevBtn.addActionListener(e -> {
            if (currentIndex > 0) {
                startNewSong(currentIndex - 1);
            } else if (currentIndex == 0 && playlist.length > 0) {
                // If we are on the first song, loop back to the very last song!
                startNewSong(playlist.length - 1);
            }
        });

        // play/pause
        playPauseBtn = createIconButton("▶", 36);
        playPauseBtn.addActionListener(e -> {
            if (currentIndex == -1) return; // nothing selected yet

            if (isPlaying) {
                AudioPlayer.pauseSong();
                playPauseBtn.setText("▶");
                isPlaying = false;
            } else {
                AudioPlayer.resumeSong();
                playPauseBtn.setText("⏸");
                isPlaying = true;
            }
        });

        // next
        JButton nextBtn = createIconButton("⏭", 28);
        nextBtn.addActionListener(e -> {
            if (currentIndex < playlist.length - 1) {
                startNewSong(currentIndex + 1);
            }
        });

        // repeat
        JButton repeatBtn = createIconButton("🔁", 28);
        repeatBtn.addActionListener(e -> {
            if (currentIndex != -1) {
                startNewSong(currentIndex);
            }
        });

        controlsPanel.add(shuffleBtn);
        controlsPanel.add(prevBtn);
        controlsPanel.add(playPauseBtn);
        controlsPanel.add(nextBtn);
        controlsPanel.add(repeatBtn);

        bottomPanel.add(controlsPanel);
        add(bottomPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void startNewSong(int index) {
        currentIndex = index;
        String songTitle = playlist[index];

        titleLabel.setText(songTitle);
        artistLabel.setText("Now Playing");

        new Thread(() -> AudioPlayer.playSong(songTitle)).start();

        isPlaying = true;
        playPauseBtn.setText("⏸");
    }

    private JButton createIconButton(String text, int size) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, size));
        btn.setForeground(Color.WHITE);
        btn.setBackground(Color.BLACK);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(0, 0, 0, 0));
        return btn;
    }
}