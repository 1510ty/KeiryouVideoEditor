
package com.mc1510ty.KeiryouVideoEditor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Launcher {

    private static final String MAIN_CLASS =
            "com.mc1510ty.KeiryouVideoEditor.Main";

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of(
            "windows-x86_64",
            "windows-aarch64",

            "linux-x86_64",
            "linux-aarch64",
            "linux-ppc64le",
            "linux-riscv64",

            "macos-x86_64",
            "macos-aarch64"
    );

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {

        // ========================================
        // 1. Platform detection
        // ========================================

        String platform = detectPlatform();

        System.out.println("Detected platform: " + platform);

        // ========================================
        // 2. Locate application JAR
        // ========================================

        Path applicationJar = Path.of(
                Launcher.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        );

        if (!Files.isRegularFile(applicationJar)) {
            throw new IllegalStateException(
                    "Application must be launched from a packaged JAR: "
                            + applicationJar
            );
        }

        System.out.println(
                "Application JAR: " + applicationJar
        );

        // ========================================
        // 3. Prepare classpath
        // ========================================

        List<URL> classPath = new ArrayList<>();

        // Main application and regular Java dependencies
        classPath.add(applicationJar.toUri().toURL());

        // Temporary directory for nested JARs
        Path extractionDirectory = Files.createTempDirectory(
                "KeiryouVideoEditor-"
        );

        // Register directory first.
        // deleteOnExit removes entries in reverse registration order.
        extractionDirectory.toFile().deleteOnExit();

        int swtCount;
        int lwjglCount;

        // ========================================
        // 4. Extract platform-specific JARs
        // ========================================

        try (JarFile jar = new JarFile(applicationJar.toFile())) {

            String swtPrefix =
                    "META-INF/swt/" + platform + "/";

            String lwjglPrefix =
                    "META-INF/lwjgl/" + platform + "/";

            // SWT
            swtCount = extractNestedJars(
                    jar,
                    swtPrefix,
                    "swt",
                    extractionDirectory,
                    classPath
            );

            // LWJGL native libraries, including SDL3
            lwjglCount = extractNestedJars(
                    jar,
                    lwjglPrefix,
                    "lwjgl",
                    extractionDirectory,
                    classPath
            );
        }

        // ========================================
        // 5. Validate extracted dependencies
        // ========================================

        if (swtCount == 0) {
            throw new IllegalStateException(
                    "No SWT libraries found for " + platform
            );
        }

        if (lwjglCount == 0) {
            throw new IllegalStateException(
                    "No LWJGL native libraries found for " + platform
            );
        }

        System.out.println();
        System.out.println("Dependency loading completed.");
        System.out.println("SWT JARs: " + swtCount);
        System.out.println("LWJGL native JARs: " + lwjglCount);
        System.out.println();

        // ========================================
        // 6. Create application classloader
        // ========================================

        ClassLoader previousContextLoader =
                Thread.currentThread().getContextClassLoader();

        try (URLClassLoader applicationLoader =
                     new URLClassLoader(
                             classPath.toArray(URL[]::new),
                             ClassLoader.getPlatformClassLoader()
                     )) {

            // Make the application classloader available
            // to libraries using the context classloader.
            Thread.currentThread()
                    .setContextClassLoader(applicationLoader);

            // ========================================
            // 7. Launch Main
            // ========================================

            launchMain(applicationLoader, args);

        } finally {

            // Restore the original context classloader.
            Thread.currentThread()
                    .setContextClassLoader(previousContextLoader);
        }
    }

    // ========================================
    // Platform detection
    // ========================================

    private static String detectPlatform() {

        String osName = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        String rawArch = System.getProperty("os.arch")
                .toLowerCase(Locale.ROOT);

        String os;

        if (osName.startsWith("windows")) {
            os = "windows";

        } else if (osName.startsWith("linux")) {
            os = "linux";

        } else if (osName.contains("mac")
                || osName.contains("darwin")) {
            os = "macos";

        } else {
            throw new UnsupportedOperationException(
                    "Unsupported OS: " + osName
            );
        }

        String arch = switch (rawArch) {
            case "amd64", "x86_64" -> "x86_64";

            case "aarch64", "arm64" -> "aarch64";

            case "ppc64le" -> "ppc64le";

            case "riscv64" -> "riscv64";

            default -> throw new UnsupportedOperationException(
                    "Unsupported architecture: " + rawArch
            );
        };

        String platform = os + "-" + arch;

        if (!SUPPORTED_PLATFORMS.contains(platform)) {
            throw new UnsupportedOperationException(
                    "Unsupported platform: " + platform
            );
        }

        return platform;
    }

    // ========================================
    // Nested JAR extraction
    // ========================================

    private static int extractNestedJars(
            JarFile sourceJar,
            String resourcePrefix,
            String type,
            Path extractionDirectory,
            List<URL> classPath
    ) throws IOException {

        int extractedCount = 0;

        Enumeration<JarEntry> entries = sourceJar.entries();

        while (entries.hasMoreElements()) {

            JarEntry entry = entries.nextElement();

            String entryName = entry.getName();

            if (entry.isDirectory()
                    || !entryName.startsWith(resourcePrefix)
                    || !entryName.endsWith(".jar")) {
                continue;
            }

            // Extract only direct child JAR files.
            String fileName = entryName.substring(
                    resourcePrefix.length()
            );

            if (fileName.isBlank() || fileName.contains("/")) {
                continue;
            }

            Path extractedJar = Files.createTempFile(
                    extractionDirectory,
                    type + "-",
                    ".jar"
            );

            try (InputStream input = sourceJar.getInputStream(entry)) {

                Files.copy(
                        input,
                        extractedJar,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // Make temporary JAR available for cleanup on JVM exit.
            extractedJar.toFile().deleteOnExit();

            classPath.add(extractedJar.toUri().toURL());

            extractedCount++;

            System.out.println(
                    "Loaded " + type.toUpperCase(Locale.ROOT)
                            + ": " + fileName
            );
        }

        return extractedCount;
    }

    // ========================================
    // Main launcher
    // ========================================

    private static void launchMain(
            ClassLoader applicationLoader,
            String[] args
    ) throws Exception {

        Class<?> mainClass = Class.forName(
                MAIN_CLASS,
                true,
                applicationLoader
        );

        try {

            // Create Main instance
            Object main = mainClass
                    .getDeclaredConstructor()
                    .newInstance();

            // Invoke Main.start(args)
            mainClass.getMethod("start", String[].class)
                    .invoke(main, (Object) args);

        } catch (InvocationTargetException e) {

            Throwable cause = e.getCause();

            if (cause instanceof Exception exception) {
                throw exception;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new RuntimeException(cause);
        }
    }
}
