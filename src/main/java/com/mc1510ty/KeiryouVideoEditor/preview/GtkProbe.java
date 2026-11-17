
package com.mc1510ty.KeiryouVideoEditor.preview;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

import org.eclipse.swt.widgets.Canvas;

public class GtkProbe {

    public record WaylandHandles(
            long wlDisplay,
            long wlSurface,
            int scaleFactor
    ) {}

    public record X11Handles(
            long xDisplay,
            long xWindow,
            int scaleFactor
    ) {}

    public enum Backend {
        WAYLAND,
        X11
    }

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena ARENA = Arena.global();

    private static final SymbolLookup GTK =
            SymbolLookup.libraryLookup("libgtk-3.so.0", ARENA);

    private static final SymbolLookup GDK =
            SymbolLookup.libraryLookup("libgdk-3.so.0", ARENA);

    private static final SymbolLookup GOBJECT =
            SymbolLookup.libraryLookup("libgobject-2.0.so.0", ARENA);

    private static MethodHandle bind(
            SymbolLookup library,
            String name,
            FunctionDescriptor descriptor
    ) {
        return LINKER.downcallHandle(
                library.find(name).orElseThrow(
                        () -> new IllegalStateException(
                                "Symbol not found: " + name
                        )
                ),
                descriptor
        );
    }

    public static WaylandHandles inspectWayland(Canvas canvas) throws Throwable {

        MethodHandle realize = bind(
                GTK,
                "gtk_widget_realize",
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        MethodHandle hasWindow = bind(
                GTK,
                "gtk_widget_get_has_window",
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

        MethodHandle getWindow = bind(
                GTK,
                "gtk_widget_get_window",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MethodHandle getDisplay = bind(
                GTK,
                "gtk_widget_get_display",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MemorySegment widget = MemorySegment.ofAddress(canvas.handle);

        // GTK UIスレッド上で実行する
        realize.invokeExact(widget);

        int ownsWindow = (int) hasWindow.invokeExact(widget);

        MemorySegment gdkWindow =
                (MemorySegment) getWindow.invokeExact(widget);

        MemorySegment gdkDisplay =
                (MemorySegment) getDisplay.invokeExact(widget);

        System.out.println("=== GTK Canvas Probe ===");
        System.out.println("Widget:    0x" +
                Long.toHexString(widget.address()));

        System.out.println("Realized:  true");
        System.out.println("HasWindow: " + (ownsWindow != 0));

        System.out.println("GdkWindow: 0x" +
                Long.toHexString(gdkWindow.address()));

        System.out.println("GdkDisplay: 0x" +
                Long.toHexString(gdkDisplay.address()));

        // Waylandバックエンドか確認
        MethodHandle getWaylandType = bind(
                GDK,
                "gdk_wayland_display_get_type",
                FunctionDescriptor.of(JAVA_LONG)
        );

        MethodHandle checkInstance = bind(
                GOBJECT,
                "g_type_check_instance_is_a",
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_LONG
                )
        );

        long waylandType =
                (long) getWaylandType.invokeExact();

        int isWayland = (int) checkInstance.invokeExact(
                gdkDisplay,
                waylandType
        );

        System.out.println("Wayland:   " + (isWayland != 0));

        if (isWayland == 0) {
            throw new UnsupportedOperationException(
                    "Not using GDK Wayland backend."
            );
        }

        if (ownsWindow == 0) {
            throw new IllegalStateException(
                    "Canvas does not own an independent GdkWindow."
            );
        }

        MethodHandle getWlDisplay = bind(
                GDK,
                "gdk_wayland_display_get_wl_display",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MemorySegment wlDisplay =
                (MemorySegment) getWlDisplay.invokeExact(gdkDisplay);

        System.out.println("wl_display: 0x" +
                Long.toHexString(wlDisplay.address()));


        MethodHandle getWlSurface = bind(
                GDK,
                "gdk_wayland_window_get_wl_surface",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MethodHandle getScaleFactor = bind(
                GDK,
                "gdk_window_get_scale_factor",
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

        int scaleFactor = (int) getScaleFactor.invokeExact(gdkWindow);

        MemorySegment wlSurface =
                (MemorySegment) getWlSurface.invokeExact(gdkWindow);

        System.out.println("wl_surface: 0x" +
                Long.toHexString(wlSurface.address()));

        if (wlDisplay.address() == 0 || wlSurface.address() == 0) {
            throw new IllegalStateException("Invalid Wayland handle");
        }

        return new WaylandHandles(
                wlDisplay.address(),
                wlSurface.address(),
                scaleFactor
        );

    }

    public static X11Handles inspectX11(Canvas canvas) throws Throwable {

        MethodHandle realize = bind(
                GTK,
                "gtk_widget_realize",
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        MethodHandle hasWindow = bind(
                GTK,
                "gtk_widget_get_has_window",
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

        MethodHandle getWindow = bind(
                GTK,
                "gtk_widget_get_window",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MethodHandle getDisplay = bind(
                GTK,
                "gtk_widget_get_display",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MemorySegment widget = MemorySegment.ofAddress(canvas.handle);

        // GTK UIスレッド上で実行
        realize.invokeExact(widget);

        int ownsWindow = (int) hasWindow.invokeExact(widget);

        if (ownsWindow == 0) {
            throw new IllegalStateException(
                    "Canvas does not own an independent GdkWindow."
            );
        }

        MemorySegment gdkWindow =
                (MemorySegment) getWindow.invokeExact(widget);

        MemorySegment gdkDisplay =
                (MemorySegment) getDisplay.invokeExact(widget);

        if (gdkWindow.address() == 0 || gdkDisplay.address() == 0) {
            throw new IllegalStateException("Invalid GTK handles");
        }

        // X11バックエンドの型を取得
        MethodHandle getX11Type = bind(
                GDK,
                "gdk_x11_display_get_type",
                FunctionDescriptor.of(JAVA_LONG)
        );

        MethodHandle checkInstance = bind(
                GOBJECT,
                "g_type_check_instance_is_a",
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_LONG
                )
        );

        long x11Type = (long) getX11Type.invokeExact();

        int isX11 = (int) checkInstance.invokeExact(
                gdkDisplay,
                x11Type
        );

        System.out.println("=== GTK X11 Canvas Probe ===");
        System.out.println("Widget:    0x"
                + Long.toHexString(widget.address()));
        System.out.println("GdkWindow: 0x"
                + Long.toHexString(gdkWindow.address()));
        System.out.println("GdkDisplay: 0x"
                + Long.toHexString(gdkDisplay.address()));
        System.out.println("X11:       " + (isX11 != 0));

        if (isX11 == 0) {
            throw new UnsupportedOperationException(
                    "Not using GDK X11 backend."
            );
        }

        // GDKのX11 Display* を取得
        MethodHandle getXDisplay = bind(
                GDK,
                "gdk_x11_display_get_xdisplay",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MemorySegment xDisplay =
                (MemorySegment) getXDisplay.invokeExact(gdkDisplay);

        // GdkWindowからX11 Window IDを取得
        MethodHandle getXid = bind(
                GDK,
                "gdk_x11_window_get_xid",
                FunctionDescriptor.of(JAVA_LONG, ADDRESS)
        );

        long xWindow = (long) getXid.invokeExact(gdkWindow);

        // DPIスケール
        MethodHandle getScaleFactor = bind(
                GDK,
                "gdk_window_get_scale_factor",
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

        int scaleFactor = (int) getScaleFactor.invokeExact(gdkWindow);

        if (xDisplay.address() == 0 || xWindow == 0) {
            throw new IllegalStateException("Invalid X11 handles");
        }

        System.out.println("X Display: 0x"
                + Long.toHexString(xDisplay.address()));
        System.out.println("X Window:  0x"
                + Long.toHexString(xWindow));
        System.out.println("Scale:     " + scaleFactor);

        return new X11Handles(
                xDisplay.address(),
                xWindow,
                scaleFactor
        );
    }

    private static boolean isDisplayType(
            MemorySegment display,
            String typeFunction
    ) throws Throwable {

        // 対応するバックエンドのシンボルが存在するか確認
        var symbol = GDK.find(typeFunction);

        if (symbol.isEmpty()) {
            return false;
        }

        // GDKの型情報を取得
        MethodHandle getType = LINKER.downcallHandle(
                symbol.get(),
                FunctionDescriptor.of(JAVA_LONG)
        );

        long type = (long) getType.invokeExact();

        // GObjectの型チェック
        MethodHandle checkInstance = bind(
                GOBJECT,
                "g_type_check_instance_is_a",
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_LONG
                )
        );

        return (int) checkInstance.invokeExact(
                display,
                type
        ) != 0;
    }

    public static Backend detectBackend(Canvas canvas) throws Throwable {

        MemorySegment widget = MemorySegment.ofAddress(canvas.handle);

        MethodHandle getRealized = bind(
                GTK,
                "gtk_widget_get_realized",
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

        MethodHandle realize = bind(
                GTK,
                "gtk_widget_realize",
                FunctionDescriptor.ofVoid(ADDRESS)
        );

        // 未RealizeならRealizeする
        if ((int) getRealized.invokeExact(widget) == 0) {
            realize.invokeExact(widget);
        }

        MethodHandle getDisplay = bind(
                GTK,
                "gtk_widget_get_display",
                FunctionDescriptor.of(ADDRESS, ADDRESS)
        );

        MemorySegment display =
                (MemorySegment) getDisplay.invokeExact(widget);

        if (display.address() == 0) {
            throw new IllegalStateException("Invalid GdkDisplay");
        }

        // Wayland判定
        if (isDisplayType(
                display,
                "gdk_wayland_display_get_type"
        )) {
            System.out.println("Detected GDK backend: Wayland");
            return Backend.WAYLAND;
        }

        // X11判定
        if (isDisplayType(
                display,
                "gdk_x11_display_get_type"
        )) {
            System.out.println("Detected GDK backend: X11");
            return Backend.X11;
        }

        throw new UnsupportedOperationException(
                "Unsupported GDK backend"
        );
    }
}
