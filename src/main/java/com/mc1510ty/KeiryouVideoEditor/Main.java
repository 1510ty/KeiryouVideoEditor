package com.mc1510ty.KeiryouVideoEditor;

import com.mc1510ty.KeiryouVideoEditor.gui.GUI;
import com.mc1510ty.KeiryouVideoEditor.preview.GtkProbe;
import com.mc1510ty.KeiryouVideoEditor.preview.Preview;

public class Main {

    private GUI gui;
    private Preview preview;

    public void start(String[] args) {
        gui = new GUI();
        preview = new Preview();

        try {
            gui.init();

            // GTK側のRealize・イベント処理を進める
            gui.processEvents();

            // SDL3をSWT Canvasに接続
            preview.init(gui.getPreviewCanvas());

            while (gui.isRunning() && preview.isRunning()) {
                gui.processEvents();
                preview.processEvents();
            }

        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            preview.dispose();
            gui.dispose();
        }
    }
}