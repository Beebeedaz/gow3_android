package com.gow3.android.data;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;

/** Android 用户数据根；与可由 APK 重新安装的 gow3port runtime 分离。 */
public final class Gow3Data {
    public static final String ROOT_NAME = "gow3data";
    public static final String USER = "user";
    public static final String CONFIG = "gow3.ini";
    public static final String LOGS = "logs";
    public static final String LOSSLESS_DLL = "Lossless.dll";
    private static final String MARKER = ".data-schema";
    private static final String SCHEMA = "1\n";

    private Gow3Data() {}

    public static File root(Context context) { return root(context.getFilesDir()); }
    public static File root(File files) { return new File(files, ROOT_NAME); }
    public static File user(Context context) { return new File(root(context), USER); }
    public static File user(File files) { return new File(root(files), USER); }
    public static File config(Context context) { return new File(root(context), CONFIG); }
    public static File config(File files) { return new File(root(files), CONFIG); }
    public static File logs(Context context) { return new File(root(context), LOGS); }
    public static File logs(File files) { return new File(root(files), LOGS); }
    public static File lossless(Context context) { return new File(root(context), LOSSLESS_DLL); }
    public static File lossless(File files) { return new File(root(files), LOSSLESS_DLL); }

    /**
     * Creates the stable data root and migrates only missing items from the old runtime tree.
     * The old source is deliberately retained: runtime promotion owns its later cleanup.
     */
    public static synchronized void prepare(File files, File defaultConfig) throws Exception {
        File data = root(files);
        if (!data.exists() && !data.mkdirs()) throw new IOException("Cannot create GOW3 data directory");
        ensureDirectory(new File(data, USER));
        ensureDirectory(new File(data, LOGS));

        File old = new File(files, "gow3port");
        migrateDirectory(new File(old, USER), new File(data, USER));
        migrateFile(new File(old, CONFIG), new File(data, CONFIG));
        migrateFile(new File(old, LOSSLESS_DLL), new File(data, LOSSLESS_DLL));
        migrateDirectory(new File(old, LOGS), new File(data, LOGS));
        if (!new File(data, CONFIG).isFile() && defaultConfig != null && defaultConfig.isFile())
            copyFileAtomically(defaultConfig, new File(data, CONFIG));
        if (!new File(data, CONFIG).isFile()) throw new IOException("Cannot create GOW3 configuration");
        Files.write(new File(data, MARKER).toPath(), SCHEMA.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
    }

    private static void migrateFile(File source, File target) throws Exception {
        if (!source.isFile() || target.exists()) return;
        copyFileAtomically(source, target);
    }

    private static void migrateDirectory(File source, File target) throws Exception {
        if (!source.isDirectory() || target.exists() && hasFiles(target)) return;
        if (!source.isDirectory()) return;
        File staging = new File(target.getParentFile(), target.getName() + ".migrating");
        deleteTree(staging);
        copyTree(source, staging);
        if (target.exists()) {
            deleteTree(target);
            if (target.exists()) { deleteTree(staging); throw new IOException("Cannot replace " + target); }
        }
        if (!staging.renameTo(target)) throw new IOException("Cannot publish " + target);
    }

    private static boolean hasFiles(File root) {
        File[] entries = root.listFiles();
        return entries != null && entries.length > 0;
    }

    private static void copyFileAtomically(File source, File target) throws Exception {
        File temporary = new File(target.getPath() + ".migrating");
        if (temporary.exists() && !temporary.delete()) throw new IOException("Cannot replace " + temporary);
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
            out.getFD().sync();
        }
        if (!sameFile(source, temporary)) throw new IOException("User data verification failed: " + source.getName());
        moveAtomically(temporary.toPath(), target.toPath());
    }

    private static boolean sameFile(File a, File b) throws Exception {
        if (a.length() != b.length()) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return java.util.Arrays.equals(hash(a, digest), hash(b, MessageDigest.getInstance("SHA-256")));
    }

    private static byte[] hash(File file, MessageDigest digest) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        return digest.digest();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyTree(File source, File target) throws Exception {
        Files.walkFileTree(source.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.toPath().relativize(dir);
                Files.createDirectories(target.toPath().resolve(relative));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.toPath().relativize(file);
                Files.copy(file, target.toPath().resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteTree(File root) throws Exception {
        if (!root.exists()) return;
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Files.delete(path); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(path); return FileVisitResult.CONTINUE;
            }
        });
    }
}
