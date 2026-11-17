package com.mc1510ty.KeiryouVideoEditor.preview;

import org.eclipse.swt.widgets.Canvas;
import org.lwjgl.sdl.SDL_Event;

import java.util.Locale;

import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLInit.SDL_Quit;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;

import static org.lwjgl.sdl.SDLError.SDL_GetError;

import static org.lwjgl.sdl.SDLEvents.SDL_PollEvent;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_QUIT;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED;

import static org.lwjgl.sdl.SDLProperties.SDL_CreateProperties;
import static org.lwjgl.sdl.SDLProperties.SDL_DestroyProperties;
import static org.lwjgl.sdl.SDLProperties.SDL_SetPointerProperty;
import static org.lwjgl.sdl.SDLProperties.SDL_SetBooleanProperty;

import org.eclipse.swt.graphics.Point;

import static org.lwjgl.sdl.SDLProperties.SDL_GetGlobalProperties;
import static org.lwjgl.sdl.SDLProperties.SDL_SetNumberProperty;

import static org.lwjgl.sdl.SDLInit.SDL_INIT_EVENTS;
import static org.lwjgl.sdl.SDLVideo.*;

public class Preview {

    private long window;

    private boolean initialized;
    private boolean running;

    private Canvas canvas;
    private int pixelScale = 1;
    private int lastWidth;
    private int lastHeight;

    public void init(Canvas canvas) throws Throwable {

        String os = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        if (os.startsWith("windows")) {

            initWindows(canvas);

        } else if (os.startsWith("linux")) {

            GtkProbe.Backend backend =
                    GtkProbe.detectBackend(canvas);

            switch (backend) {

                case WAYLAND ->
                        initWayland(canvas);

                case X11 ->
                        initX11(canvas);
            }

        } else {

            throw new UnsupportedOperationException(
                    "Unsupported platform: " + os
            );
        }

        running = true;
    }

    private void initWindows(Canvas canvas) {

        // 現段階ではWindows用の実装
        String os = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        if (!os.startsWith("windows")) {
            throw new UnsupportedOperationException(
                    "Native preview embedding is not implemented for: " + os
            );
        }

        // SWT CanvasのネイティブHWNDを取得
        // Control.handleはSWTの内部フィールド
        long hwnd = canvas.handle;

        if (hwnd == 0L) {
            throw new IllegalStateException("Invalid HWND");
        }

        if (!SDL_Init(SDL_INIT_VIDEO)) {
            throw new RuntimeException(
                    "SDL_Init failed: " + SDL_GetError()
            );
        }

        initialized = true;

        int props = SDL_CreateProperties();

        if (props == 0) {
            throw new RuntimeException(
                    "SDL_CreateProperties failed: " + SDL_GetError()
            );
        }

        try {
            // 既存のSWT CanvasをSDL3に渡す
            if (!SDL_SetPointerProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_WIN32_HWND_POINTER,
                    hwnd
            )) {
                throw new RuntimeException(
                        "SDL_SetPointerProperty failed: " + SDL_GetError()
                );
            }

            // 将来的にVulkanで使用する
            if (!SDL_SetBooleanProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN,
                    true
            )) {
                throw new RuntimeException(
                        "SDL_SetBooleanProperty failed: " + SDL_GetError()
                );
            }

            window = SDL_CreateWindowWithProperties(props);

        } finally {
            SDL_DestroyProperties(props);
        }

        if (window == 0L) {
            throw new RuntimeException(
                    "SDL_CreateWindowWithProperties failed: " + SDL_GetError()
            );
        }

        running = true;

        System.out.println("SDL3 embedded successfully!");
        System.out.println("HWND: 0x" + Long.toHexString(hwnd));
    }

    private void initWayland(Canvas canvas) throws Throwable {
        this.canvas = canvas;

        // GTKからWaylandのネイティブハンドルを取得
        GtkProbe.WaylandHandles handles = GtkProbe.inspectWayland(canvas);

        pixelScale = Math.max(1, handles.scaleFactor());

        // SDLのグローバルプロパティを取得
        int globalProps = SDL_GetGlobalProperties();

        if (globalProps == 0) {
            throw new RuntimeException(
                    "SDL_GetGlobalProperties failed"
            );
        }

        // SDLがGTKと同じwl_displayを使用するように設定
        // 必ずSDL_Initより前に設定する
        if (!SDL_SetPointerProperty(
                globalProps,
                SDL_PROP_GLOBAL_VIDEO_WAYLAND_WL_DISPLAY_POINTER,
                handles.wlDisplay()
        )) {
            throw new RuntimeException(
                    "Failed to set Wayland display: " + SDL_GetError()
            );
        }

        // SDL初期化
        if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) {
            throw new RuntimeException(
                    "SDL_Init failed: " + SDL_GetError()
            );
        }

        initialized = true;

        Point size = canvas.getSize();

        int width = Math.max(1, size.x * pixelScale);
        int height = Math.max(1, size.y * pixelScale);

        int props = SDL_CreateProperties();

        if (props == 0) {
            throw new RuntimeException(
                    "SDL_CreateProperties failed: " + SDL_GetError()
            );
        }

        try {
            // SWT Canvasの既存wl_surfaceをSDLに渡す
            if (!SDL_SetPointerProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_WAYLAND_WL_SURFACE_POINTER,
                    handles.wlSurface()
            )) {
                throw new RuntimeException(
                        "Failed to set Wayland surface: " + SDL_GetError()
                );
            }

            // Vulkan対応ウィンドウとして作成
            if (!SDL_SetBooleanProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN,
                    true
            )) {
                throw new RuntimeException(
                        "Failed to set Vulkan property: " + SDL_GetError()
                );
            }

            // 初期サイズ
            if (!SDL_SetNumberProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER,
                    width
            )) {
                throw new RuntimeException(
                        "Failed to set window width: " + SDL_GetError()
                );
            }

            if (!SDL_SetNumberProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER,
                    height
            )) {
                throw new RuntimeException(
                        "Failed to set window height: " + SDL_GetError()
                );
            }

            // 既存SurfaceをラップしたSDL_Windowを作成
            window = SDL_CreateWindowWithProperties(props);

        } finally {
            SDL_DestroyProperties(props);
        }

        if (window == 0L) {
            throw new RuntimeException(
                    "SDL_CreateWindowWithProperties failed: " + SDL_GetError()
            );
        }

        lastWidth = width;
        lastHeight = height;

        System.out.println("SDL3 Wayland embedding initialized!");
        System.out.println("wl_display: 0x"
                + Long.toHexString(handles.wlDisplay()));
        System.out.println("wl_surface: 0x"
                + Long.toHexString(handles.wlSurface()));
        System.out.println("SDL_Window: 0x"
                + Long.toHexString(window));
    }

    private void initX11(Canvas canvas) throws Throwable {
        this.canvas = canvas;

        // GTKからX11ハンドルを取得
        GtkProbe.X11Handles handles = GtkProbe.inspectX11(canvas);

        pixelScale = Math.max(1, handles.scaleFactor());

        // SDL初期化
        if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) {
            throw new RuntimeException(
                    "SDL_Init failed: " + SDL_GetError()
            );
        }

        initialized = true;

        Point size = canvas.getSize();

        int width = Math.max(1, size.x * pixelScale);
        int height = Math.max(1, size.y * pixelScale);

        int props = SDL_CreateProperties();

        if (props == 0) {
            throw new RuntimeException(
                    "SDL_CreateProperties failed: " + SDL_GetError()
            );
        }

        try {
            // 既存のSWT CanvasのX11 Window IDを渡す
            if (!SDL_SetNumberProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_X11_WINDOW_NUMBER,
                    handles.xWindow()
            )) {
                throw new RuntimeException(
                        "Failed to set X11 window: " + SDL_GetError()
                );
            }

            // Vulkan対応
            if (!SDL_SetBooleanProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN,
                    true
            )) {
                throw new RuntimeException(
                        "Failed to set Vulkan property: " + SDL_GetError()
                );
            }

            // サイズ
            if (!SDL_SetNumberProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER,
                    width
            )) {
                throw new RuntimeException(
                        "Failed to set width: " + SDL_GetError()
                );
            }

            if (!SDL_SetNumberProperty(
                    props,
                    SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER,
                    height
            )) {
                throw new RuntimeException(
                        "Failed to set height: " + SDL_GetError()
                );
            }

            // SDL_Window作成
            window = SDL_CreateWindowWithProperties(props);

        } finally {
            SDL_DestroyProperties(props);
        }

        if (window == 0L) {
            throw new RuntimeException(
                    "SDL_CreateWindowWithProperties failed: " + SDL_GetError()
            );
        }

        lastWidth = width;
        lastHeight = height;

        System.out.println("SDL3 X11 embedding initialized!");
        System.out.println("X Display: 0x"
                + Long.toHexString(handles.xDisplay()));
        System.out.println("X Window: 0x"
                + Long.toHexString(handles.xWindow()));
        System.out.println("SDL_Window: 0x"
                + Long.toHexString(window));
    }

    public void processEvents() {
        try (SDL_Event event = SDL_Event.calloc()) {
            while (SDL_PollEvent(event)) {
                int type = event.type();

                if (type == SDL_EVENT_QUIT ||
                        type == SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
                    running = false;
                }

            }
        }
        syncSize();
    }

    public boolean isRunning() {
        return running;
    }

    public void dispose() {
        running = false;

        if (window != 0L) {
            SDL_DestroyWindow(window);
            window = 0L;
        }

        if (initialized) {
            SDL_Quit();
            initialized = false;
        }
    }

    private void syncSize() {
        if (canvas == null || canvas.isDisposed() || window == 0L) {
            return;
        }

        Point size = canvas.getSize();

        int width = Math.max(1, size.x * pixelScale);
        int height = Math.max(1, size.y * pixelScale);

        if (width == lastWidth && height == lastHeight) {
            return;
        }

        if (!SDL_SetWindowSize(window, width, height)) {
            throw new RuntimeException(
                    "SDL_SetWindowSize failed: " + SDL_GetError()
            );
        }

        lastWidth = width;
        lastHeight = height;
    }
}