package com.musicplayer;

import com.musicplayer.backend.DatabaseCleaner;
import com.musicplayer.backend.MusicScanner;
import com.musicplayer.frontend.GUI;

public class App { //MAIN
    public static void main(String[] args) {
        System.out.println("connecting to raspbery pi database:");

          DatabaseCleaner.clean();
          MusicScanner.startScan();

        new GUI();
    }
}
