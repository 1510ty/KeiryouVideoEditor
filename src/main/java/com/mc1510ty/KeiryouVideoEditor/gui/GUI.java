package com.mc1510ty.KeiryouVideoEditor.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class GUI {

    private Display display;
    private Shell shell;

    private Composite previewArea;
    private Canvas previewCanvas;

    public void init() {
        display = new Display();
        shell = new Shell(display);

        shell.setText("KeiryouVideoEditor");
        shell.setSize(960, 720);
        shell.setMinimumSize(400, 300);
        shell.setLayout(new FillLayout());

        // 上下分割
        SashForm sashForm = new SashForm(shell, SWT.VERTICAL | SWT.SMOOTH);
        sashForm.setSashWidth(6);

        // プレビュー領域
        previewArea = new Composite(sashForm, SWT.NONE);
        previewArea.setLayout(null);
        previewArea.setBackground(display.getSystemColor(SWT.COLOR_BLACK));

        // SDL3に渡すCanvas
        previewCanvas = new Canvas(previewArea, SWT.NO_BACKGROUND);

        // タイムライン領域
        Composite timelineArea = new Composite(sashForm, SWT.BORDER);
        timelineArea.setLayout(new FillLayout());

        Label timelineLabel = new Label(timelineArea, SWT.CENTER);
        timelineLabel.setText("Timeline");

        // 初期比率：プレビュー60%、タイムライン40%
        sashForm.setWeights(60, 40);

        // プレビュー領域のサイズ変更に追従
        previewArea.addListener(SWT.Resize, event -> {
            layoutPreviewCanvas();
        });

        shell.open();
        shell.layout(true, true);

        layoutPreviewCanvas();
    }

    /**
     * プレビュー領域内に最大サイズの16:9 Canvasを配置する
     */
    private void layoutPreviewCanvas() {
        if (previewArea == null || previewArea.isDisposed()) {
            return;
        }

        Rectangle area = previewArea.getClientArea();

        if (area.width <= 0 || area.height <= 0) {
            return;
        }

        // 16:9で収まる最大サイズを計算
        double scale = Math.min(
                area.width / 16.0,
                area.height / 9.0
        );

        int width = Math.max(1, (int) Math.round(16.0 * scale));
        int height = Math.max(1, (int) Math.round(9.0 * scale));

        // 中央配置
        int x = (area.width - width) / 2;
        int y = (area.height - height) / 2;

        previewCanvas.setBounds(x, y, width, height);
    }

    public void processEvents() {
        while (display.readAndDispatch()) {
        }
    }

    public void dispose() {
        if (display != null && !display.isDisposed()) {
            display.dispose();
        }
    }

    public boolean isRunning() {
        return shell != null && !shell.isDisposed();
    }

    public Canvas getPreviewCanvas() {
        return previewCanvas;
    }
}