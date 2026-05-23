package com.musicplayer.frontend;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.lang.reflect.Method;
import com.musicplayer.backend.AudioPlayer;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;

public class GUI extends JFrame {

    private JLabel albumArtLabel;
    private JLabel titleLabel;
    private JLabel artistLabel;
    private JLabel albumLabel;
    private JLabel yearLabel;
    private JButton playPauseBtn;
    private JTable songTableView;
    private JTree libraryTree;
    private DefaultMutableTreeNode rootNode;

    private String currentSongTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private boolean isPlaying = false;

    // progress slider var
    private JSlider progressSlider;
    private JLabel timeStart;
    private JLabel timeEnd;
    private javax.swing.Timer progressTimer;
    private boolean seeking = false;

    public GUI() {
        setTitle("bbbrfbnbbb");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        // Menu Bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(Color.BLACK);
        menuBar.setForeground(Color.WHITE);

        JMenu fileMenu = new JMenu("File");
        fileMenu.setForeground(Color.WHITE);
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        setJMenuBar(menuBar);

        // Cmain content panel with split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setBackground(new Color(40, 40, 40));
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setDividerSize(8);
        styleSplitPane(mainSplitPane);

        // left - Library Tree
        JPanel leftPanel = createLibraryPanel();
        mainSplitPane.setLeftComponent(leftPanel);

        // right pannel song list pannel art
        JPanel rightPanel = createSongPanel();
        mainSplitPane.setRightComponent(rightPanel);

        add(mainSplitPane, BorderLayout.CENTER);

        // bottom pannel control
        JPanel bottomPanel = createControlsPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private Icon makeEmojiIcon(String emoji) {
    return new Icon() {
        public int getIconWidth()  { return 12; }  
        public int getIconHeight() { return 18; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            g.setColor(new Color(50, 205, 50));
            g.drawString(emoji, x, y + 14);
        }
    };
}

    private JPanel createLibraryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        // library tree
        rootNode = new DefaultMutableTreeNode("Library");
        String[] artists = AudioPlayer.getArtists();

        for (String artist : artists) {
            DefaultMutableTreeNode artistNode = new DefaultMutableTreeNode(artist);
            String[] albums = AudioPlayer.getAlbumsForArtist(artist);

            for (String album : albums) {
                DefaultMutableTreeNode albumNode = new DefaultMutableTreeNode(album);
                String[] songs = AudioPlayer.getSongsForAlbum(artist, album);

                for (String song : songs) {
                    albumNode.add(new DefaultMutableTreeNode(song));
                }

                artistNode.add(albumNode);
            }

            rootNode.add(artistNode);
        }

        libraryTree = new JTree(rootNode);
libraryTree.setBackground(Color.BLACK);
libraryTree.setForeground(new Color(50, 205, 50));
libraryTree.setOpaque(true);
libraryTree.setRowHeight(22);
libraryTree.setCellRenderer(new DefaultTreeCellRenderer() {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        int depth = node.getLevel();

        setBackground(sel ? new Color(100, 100, 150) : Color.BLACK);
        setForeground(sel ? Color.WHITE : new Color(50, 205, 50));
        setOpaque(true);
        setIcon(null); // no separate icon, emoji is part of the text

        String label = value.toString();
        switch (depth) {
            case 0: setText("🎧 " + label); break; // Library
            case 1: setText("👤 " + label); break; // Artist
            case 2: setText("🎵 " + label); break; // Album
            case 3: setText("🎶 " + label); break; // Song
            default: setText(label); break;
        }

        setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));

        return this;
    }
});

        libraryTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = libraryTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

                        if (node.isLeaf() && node.getParent() != null &&
                            node.getParent().getParent() != null) {
                            String songTitle = node.toString();
                            DefaultMutableTreeNode albumNode = (DefaultMutableTreeNode) node.getParent();
                            DefaultMutableTreeNode artistNode = (DefaultMutableTreeNode) albumNode.getParent();

                            playSong(songTitle, artistNode.toString(), albumNode.toString());
                        }
                    }
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(libraryTree);
        treeScroll.setBackground(Color.BLACK);
        treeScroll.getViewport().setBackground(Color.BLACK);
        styleScrollBar(treeScroll);

        JLabel libraryLabel = new JLabel("  Library");
        libraryLabel.setBackground(Color.BLACK);
        libraryLabel.setForeground(new Color(50, 205, 50));
        libraryLabel.setOpaque(true);

        panel.add(libraryLabel, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSongPanel() 
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        // song pannel nr and title
        String[] columnNames = {"#", "Title"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        songTableView = new JTable(tableModel);
        songTableView.setBackground(Color.BLACK);
        songTableView.setForeground(new Color(50, 205, 50));
        songTableView.setSelectionBackground(new Color(100, 100, 150));
        songTableView.setSelectionForeground(Color.WHITE);
        songTableView.setGridColor(new Color(50, 50, 50));
        songTableView.setShowGrid(true);
        songTableView.setRowHeight(25);
        songTableView.getTableHeader().setBackground(Color.BLACK);
        songTableView.getTableHeader().setForeground(new Color(50, 205, 50));
        songTableView.getTableHeader().setOpaque(true);

        songTableView.getColumnModel().getColumn(0).setPreferredWidth(40);
        songTableView.getColumnModel().getColumn(1).setPreferredWidth(450);

        songTableView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = songTableView.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        String songTitle = (String) songTableView.getValueAt(row, 1);
                        playSong(songTitle, currentArtist, currentAlbum);
                    }
                }
            }
        });

        JScrollPane songScroll = new JScrollPane(songTableView);
        songScroll.setBackground(Color.BLACK);
        songScroll.getViewport().setBackground(Color.BLACK);
        styleScrollBar(songScroll);

        // left side of top pannel artwork
        JPanel artPanel = new JPanel(new BorderLayout());
        artPanel.setBackground(Color.BLACK);
        artPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        albumArtLabel = new JLabel();
        albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
        albumArtLabel.setVerticalAlignment(SwingConstants.CENTER);
        albumArtLabel.setPreferredSize(new Dimension(180, 180));
        albumArtLabel.setBackground(new Color(20, 20, 20));
        albumArtLabel.setOpaque(true);
        albumArtLabel.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 1));

        artPanel.add(albumArtLabel, BorderLayout.CENTER);

        // song info right side
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(Color.BLACK);
        infoPanel.setBorder(BorderFactory.createEmptyBorder(20, 15, 10, 10));

        titleLabel = new JLabel("Select a song");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(60, 60, 60));
        sep.setBackground(new Color(60, 60, 60));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);

        artistLabel = new JLabel("Artist: —");
        artistLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        artistLabel.setForeground(new Color(150, 150, 150));
        artistLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        albumLabel = new JLabel("Album: —");
        albumLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        albumLabel.setForeground(new Color(150, 150, 150));
        albumLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        yearLabel = new JLabel("Year: —");
        yearLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        yearLabel.setForeground(new Color(150, 150, 150));
        yearLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(titleLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        infoPanel.add(sep);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        infoPanel.add(artistLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        infoPanel.add(albumLabel);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        infoPanel.add(yearLabel);
        infoPanel.add(Box.createVerticalGlue());

        // horizontal split art|info
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        topSplit.setDividerLocation(210);
        topSplit.setDividerSize(6);
        styleSplitPane(topSplit);
        topSplit.setLeftComponent(artPanel);
        topSplit.setRightComponent(infoPanel);

        // vertical split: top info | song list
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setBackground(new Color(40, 40, 40));
        mainSplit.setDividerLocation(220);
        mainSplit.setDividerSize(8);
        styleSplitPane(mainSplit);
        mainSplit.setTopComponent(topSplit);
        mainSplit.setBottomComponent(songScroll);

        panel.add(mainSplit, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.BLACK);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // progress bar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(Color.BLACK);

        timeStart = new JLabel("00:00");
        timeStart.setForeground(new Color(50, 205, 50));
        timeEnd = new JLabel("00:00");
        timeEnd.setForeground(new Color(50, 205, 50));

        progressSlider = new JSlider(0, 1000, 0);
        progressSlider.setBackground(Color.BLACK);
        progressSlider.setUI(new javax.swing.plaf.basic.BasicSliderUI(progressSlider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int trackY = trackRect.y + trackRect.height / 2;
                g2.setColor(new Color(50, 50, 50));
                g2.fillRoundRect(trackRect.x, trackY - 2, trackRect.width, 4, 4, 4);
                int filledWidth = thumbRect.x + thumbRect.width / 2 - trackRect.x;
                g2.setColor(new Color(50, 205, 50));
                g2.fillRoundRect(trackRect.x, trackY - 2, filledWidth, 4, 4, 4);
            }
            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = thumbRect.x + thumbRect.width / 2;
                int cy = thumbRect.y + thumbRect.height / 2;
                g2.setColor(new Color(50, 205, 50));
                g2.fillOval(cx - 5, cy - 5, 10, 10);
            }
            @Override
            public void paintFocus(Graphics g) {}
        });
        progressSlider.setBorder(BorderFactory.createEmptyBorder());
        progressSlider.setOpaque(false);

        progressSlider.addMouseListener(new MouseAdapter() {
    @Override
    public void mousePressed(MouseEvent e) {
        seeking = true;
        // jump  to clicked position
        int value = (int)((double) e.getX() / progressSlider.getWidth() * 1000);
        progressSlider.setValue(value);
    }
    @Override
    public void mouseReleased(MouseEvent e) {
        seeking = false;
        int value = (int)((double) e.getX() / progressSlider.getWidth() * 1000);
        progressSlider.setValue(value);
        int total = AudioPlayer.getTotalSeconds();
        if (total > 0) {
            int target = (int)(value / 1000.0 * total);
            new Thread(() -> AudioPlayer.seekTo(target)).start();
        }
    }
});

        progressPanel.add(timeStart, BorderLayout.WEST);
        progressPanel.add(progressSlider, BorderLayout.CENTER);
        progressPanel.add(timeEnd, BorderLayout.EAST);
        panel.add(progressPanel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        // shuffle  prev  play  next  repeat centered volume
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setBackground(Color.BLACK);

        // left spacer to balance volume slider
        JPanel leftSpacer = new JPanel();
        leftSpacer.setBackground(Color.BLACK);
        leftSpacer.setPreferredSize(new Dimension(160, 1));
        buttonRow.add(leftSpacer, BorderLayout.WEST);

        //  playback buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(Color.BLACK);

        JButton prevBtn = createIconButton("⏮", 24);
        prevBtn.addActionListener(e -> playPrevious());

        playPauseBtn = createIconButton("▶", 32);
        playPauseBtn.addActionListener(e -> togglePlayPause());

        JButton nextBtn = createIconButton("⏭", 24);
        nextBtn.addActionListener(e -> playNext());

        JButton repeatBtn = createIconButton("🔁", 20);
        repeatBtn.addActionListener(e -> playCurrentAgain());

        JButton shuffleBtn = createIconButton("🔀", 20);
        shuffleBtn.addActionListener(e -> shufflePlay());

        buttonPanel.add(shuffleBtn);
        buttonPanel.add(prevBtn);
        buttonPanel.add(playPauseBtn);
        buttonPanel.add(nextBtn);
        buttonPanel.add(repeatBtn);
        buttonRow.add(buttonPanel, BorderLayout.CENTER);

        //  volume icon + slider
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        volumePanel.setBackground(Color.BLACK);
        volumePanel.setPreferredSize(new Dimension(160, 1));

        JLabel volIcon = new JLabel("🔊");
        volIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        volIcon.setForeground(new Color(50, 205, 50));

        JSlider volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.setPreferredSize(new Dimension(100, 20));
        volumeSlider.setBackground(Color.BLACK);
        volumeSlider.setForeground(new Color(50, 205, 50));
        volumeSlider.setUI(new javax.swing.plaf.basic.BasicSliderUI(volumeSlider) {
    @Override
    public void paintTrack(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int trackY = trackRect.y + trackRect.height / 2;
        // Dark background track
        g2.setColor(new Color(50, 50, 50));
        g2.fillRoundRect(trackRect.x, trackY - 2, trackRect.width, 4, 4, 4);
        // color design
        int filledWidth = thumbRect.x + thumbRect.width / 2 - trackRect.x;
        g2.setColor(new Color(50, 205, 50));
        g2.fillRoundRect(trackRect.x, trackY - 2, filledWidth, 4, 4, 4);
    }
    @Override
    public void paintThumb(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // green circle
        int cx = thumbRect.x + thumbRect.width / 2;
        int cy = thumbRect.y + thumbRect.height / 2;
        int r = 5;
        g2.setColor(new Color(50, 205, 50));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
    }
    @Override
    public void paintFocus(Graphics g) {} // removes dotted border
});
volumeSlider.setBorder(BorderFactory.createEmptyBorder()); // removes white outline
volumeSlider.setOpaque(false);

        
        AudioPlayer.setVolume(volumeSlider.getValue());

        volumeSlider.addChangeListener(e -> {
            AudioPlayer.setVolume(volumeSlider.getValue());
            
            int val = volumeSlider.getValue();
            if (val == 0) {
                volIcon.setText("🔇");
            } else if (val < 40) {
                volIcon.setText("🔈");
            } else if (val < 70) {
                volIcon.setText("🔉");
            } else {
                volIcon.setText("🔊");
            }
        });

        volumePanel.add(volIcon);
        volumePanel.add(volumeSlider);
        buttonRow.add(volumePanel, BorderLayout.EAST);

        panel.add(buttonRow);

        return panel;
    }

    private void playSong(String songTitle, String artist, String album) {
        currentSongTitle = songTitle;
        currentArtist = artist;
        currentAlbum = album;

        titleLabel.setText(songTitle);
        artistLabel.setText("Artist: " + artist);
        albumLabel.setText("Album: " + album);
        yearLabel.setText("Year: —");

        // new thread for song data
        new Thread(() -> {
            try {
                Map<String, String> info = AudioPlayer.getSongInfo(songTitle);
                String year = info.getOrDefault("YEAR",
                              info.getOrDefault("DATE",
                              info.getOrDefault("ORIGINALYEAR", "")));
                String displayYear = (year == null || year.isEmpty()) ? "—" : year;
                SwingUtilities.invokeLater(() -> yearLabel.setText("Year: " + displayYear));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> yearLabel.setText("Year: —"));
            }
        }).start();

        updateAlbumArt(artist, album);

        new Thread(() -> AudioPlayer.playSong(songTitle)).start();

        isPlaying = true;
        playPauseBtn.setText("⏸");

        // start progress timer
        if (progressTimer != null) progressTimer.stop();
        progressTimer = new javax.swing.Timer(500, e -> {
            if (!seeking) {
                int total = AudioPlayer.getTotalSeconds();
                int current = AudioPlayer.getCurrentSeconds();
                if (total > 0) {
                    progressSlider.setValue((int)(current * 1000.0 / total));
                }
                timeStart.setText(formatTime(current));
                timeEnd.setText(formatTime(total));
            }
        });
        progressTimer.start();

        updateSongList(artist, album, songTitle);
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateSongList(String artist, String album, String selectedSong) {
        String[] songs = AudioPlayer.getSongsForAlbum(artist, album);
        DefaultTableModel model = (DefaultTableModel) songTableView.getModel();
        model.setRowCount(0);

        int selectedRow = -1;
        for (int i = 0; i < songs.length; i++) {
            model.addRow(new Object[]{i + 1, songs[i]});
            if (songs[i].equals(selectedSong)) {
                selectedRow = i;
            }
        }

        if (selectedRow >= 0) {
            songTableView.setRowSelectionInterval(selectedRow, selectedRow);
            songTableView.scrollRectToVisible(songTableView.getCellRect(selectedRow, 0, true));
        }
    }

    private void updateAlbumArt(String artist, String album) {
        new Thread(() -> {
            try {
                String[] songs = AudioPlayer.getSongsForAlbum(artist, album);
                if (songs.length > 0) {
                    Map<String, String> songInfo = AudioPlayer.getSongInfo(songs[0]);
                    String filePath = songInfo.get("FILE_PATH");

                    if (filePath != null && !filePath.isEmpty()) {
                        String windowsPath = filePath.replace("/mnt/HDD1TB", "X:").replace("/", "\\");
                        File audioFile = new File(windowsPath);

                        if (audioFile.exists()) {
                            AudioFile af = AudioFileIO.read(audioFile);
                            Tag tag = af.getTag();

                            if (tag != null) {
                                try {
                                    Method getFirstArtworkMethod = tag.getClass().getMethod("getFirstArtwork");
                                    Object artwork = getFirstArtworkMethod.invoke(tag);

                                    if (artwork != null) {
                                        Method getBinaryDataMethod = artwork.getClass().getMethod("getBinaryData");
                                        byte[] imageData = (byte[]) getBinaryDataMethod.invoke(artwork);

                                        ImageIcon icon = new ImageIcon(imageData);
                                        Image scaledImg = icon.getImage().getScaledInstance(180, 180, Image.SCALE_SMOOTH);

                                        SwingUtilities.invokeLater(() -> {
                                            albumArtLabel.setIcon(new ImageIcon(scaledImg));
                                            albumArtLabel.setText("");
                                        });
                                        return;
                                    }
                                } catch (Exception e) {
                                    // artwork reflection failed, fall through
                                }

                                SwingUtilities.invokeLater(() -> {
                                    albumArtLabel.setIcon(null);
                                    albumArtLabel.setText("No Artwork");
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not load album art: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    albumArtLabel.setIcon(null);
                    albumArtLabel.setText("No Artwork");
                });
            }
        }).start();
    }

    private void togglePlayPause() {
        if (currentSongTitle.isEmpty()) return;

        if (isPlaying) {
            AudioPlayer.pauseSong();
            playPauseBtn.setText("▶");
            isPlaying = false;
        } else {
            AudioPlayer.resumeSong();
            playPauseBtn.setText("⏸");
            isPlaying = true;
        }
    }

    private void playPrevious() {
        DefaultTableModel model = (DefaultTableModel) songTableView.getModel();
        int currentIndex = songTableView.getSelectedRow();

        if (currentIndex > 0) {
            String prevSong = (String) model.getValueAt(currentIndex - 1, 1);
            playSong(prevSong, currentArtist, currentAlbum);
        }
    }

    private void playNext() {
        DefaultTableModel model = (DefaultTableModel) songTableView.getModel();
        int currentIndex = songTableView.getSelectedRow();

        if (currentIndex < model.getRowCount() - 1) {
            String nextSong = (String) model.getValueAt(currentIndex + 1, 1);
            playSong(nextSong, currentArtist, currentAlbum);
        }
    }

    private void playCurrentAgain() {
        if (!currentSongTitle.isEmpty()) {
            playSong(currentSongTitle, currentArtist, currentAlbum);
        }
    }

    private void shufflePlay() {
        DefaultTableModel model = (DefaultTableModel) songTableView.getModel();
        if (model.getRowCount() > 1) {
            Random rand = new Random();
            int randomIndex = rand.nextInt(model.getRowCount());
            String randomSong = (String) model.getValueAt(randomIndex, 1);
            playSong(randomSong, currentArtist, currentAlbum);
        }
    }

    private JButton createIconButton(String text, int size) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, size));
        btn.setForeground(new Color(50, 205, 50));
        btn.setBackground(Color.BLACK);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(5, 5, 5, 5));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setForeground(new Color(50, 205, 50));
            }
        });

        return btn;
    }

    private void styleScrollBar(JScrollPane scrollPane) {
        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(100, 100, 100);
                trackColor = new Color(20, 20, 20);
            }
            @Override
            protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }
            @Override
            protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }
            private JButton invisibleButton() {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(0, 0));
                btn.setMinimumSize(new Dimension(0, 0));
                btn.setMaximumSize(new Dimension(0, 0));
                return btn;
            }
        });
        scrollPane.getVerticalScrollBar().setBackground(new Color(20, 20, 20));
        scrollPane.getHorizontalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(100, 100, 100);
                trackColor = new Color(20, 20, 20);
            }
            @Override
            protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }
            @Override
            protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }
            private JButton invisibleButton() {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(0, 0));
                btn.setMinimumSize(new Dimension(0, 0));
                btn.setMaximumSize(new Dimension(0, 0));
                return btn;
            }
        });
        scrollPane.getHorizontalScrollBar().setBackground(new Color(20, 20, 20));
    }

    private void styleSplitPane(JSplitPane splitPane) {
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(new Color(40, 40, 40));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        super.paint(g);
                    }
                };
            }
        });
    }   
}