package com.mc1510ty.KeiryouVideoEditor.gui;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class GUI {
    public void init() {
        Display display = new Display();

        try {
            Shell shell = new Shell(display);

            shell.setText("KeiryouVideoEditor");
            shell.setSize(640, 360);
            shell.setMinimumSize(0, 0);

            shell.open();

            while (!shell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }

        } finally {
            display.dispose();
        }
    }

}
