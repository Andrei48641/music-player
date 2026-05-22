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

    public GUI() {
        setTitle("bbbrfbnbbb");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Color.BLACK);
        setLayout(new BorderLayout());

        //menu Bar
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

        // Create main content panel with split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setBackground(new Color(40, 40, 40));
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setDividerSize(8);
        styleSplitPane(mainSplitPane);

        // LEFT PANEL artist with albums and songs tree
        JPanel leftPanel = createLibraryPanel();
        mainSplitPane.setLeftComponent(leftPanel);

        // RIGHT PANEL - Song List and Album Art
        JPanel rightPanel = createSongPanel();
        mainSplitPane.setRightComponent(rightPanel);

        add(mainSplitPane, BorderLayout.CENTER);

        // BOTTOM PANEL - Controls
        JPanel bottomPanel = createControlsPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createLibraryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        // Build library tree
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
        libraryTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setBackground(sel ? new Color(100, 100, 150) : Color.BLACK);
                setForeground(sel ? Color.WHITE : new Color(50, 205, 50));
                setOpaque(true);
                return c;
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

        JLabel libraryLabel = new JLabel("  Library");
        libraryLabel.setBackground(Color.BLACK);
        libraryLabel.setForeground(new Color(50, 205, 50));
        libraryLabel.setOpaque(true);

        panel.add(libraryLabel, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSongPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        // ── Song table ──
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

        // ── LEFT side of top panel: Album Art ──
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

        // ── RIGHT side of top panel: Song info ──
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

        // ── Horizontal split: art | info ──
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        topSplit.setDividerLocation(210);
        topSplit.setDividerSize(6);
        styleSplitPane(topSplit);
        topSplit.setLeftComponent(artPanel);
        topSplit.setRightComponent(infoPanel);

        // ── Vertical split: top info | song list ──
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

        // Progress bar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(Color.BLACK);
        JLabel timeStart = new JLabel("00:00");
        timeStart.setForeground(new Color(50, 205, 50));
        JLabel timeEnd = new JLabel("00:00");
        timeEnd.setForeground(new Color(50, 205, 50));
        JSlider slider = new JSlider(0, 100, 0);
        slider.setBackground(Color.BLACK);
        slider.setForeground(new Color(100, 150, 200));
        progressPanel.add(timeStart, BorderLayout.WEST);
        progressPanel.add(slider, BorderLayout.CENTER);
        progressPanel.add(timeEnd, BorderLayout.EAST);
        panel.add(progressPanel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Control buttons
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

        panel.add(buttonPanel);

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

        // Fetch year from metadata asynchronously
        new Thread(() -> {
            try {
                Map<String, String> info = AudioPlayer.getSongInfo(songTitle);
                // Try common tag key names for year
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

        updateSongList(artist, album, songTitle);
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