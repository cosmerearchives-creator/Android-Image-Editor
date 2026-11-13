package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * On-device project library. Each project owns a photo and its own editable depth map.
 * Working files remain where the existing renderer expects them, to preserve updates.
 */
public final class ProjectStore {
    private static final String ROOT = "vr_projects";
    private static final String PHOTO = "vr_import.png";
    private static final String DEPTH = "vr_depth_quick.png";
    private static final String TITLE = "title.txt";
    private static final String PREF = "vr_project_library";
    private static final String SELECTED = "selected_project";
    private static final String DRAFT = "draft_title";
    private ProjectStore() {}

    private static File root(Context ctx) {
        File f = new File(ctx.getFilesDir(), ROOT);
        if (!f.exists() && !f.mkdirs()) throw new IllegalStateException("Cannot create project library");
        return f;
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private static File current(Context ctx, String filename) {
        return new File(ctx.getFilesDir(), filename);
    }
    private static void copy(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = in.read(buffer)) != -1) out.write(buffer, 0, length);
            out.getFD().sync();
        }
    }
    private static void replace(File source, File target) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".new");
        try {
            copy(source, tmp);
            if (!tmp.renameTo(target)) throw new IOException("Cannot replace " + target.getName());
        } finally { tmp.delete(); }
    }
    private static String name(File directory) {
        File file = new File(directory, TITLE);
        if (!file.exists()) return directory.getName();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int)Math.min(file.length(), 80)];
            int n = in.read(data);
            return n < 1 ? directory.getName() : new String(data, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return directory.getName();
        }
    }
    private static File[] projects(Context ctx) {
        File[] entries = root(ctx).listFiles(f -> f.isDirectory() &&
            !f.getName().endsWith(".new") && new File(f, PHOTO).isFile());
        if (entries == null) return new File[0];
        Arrays.sort(entries, Comparator.comparingLong(File::lastModified).reversed());
        return entries;
    }
    public static synchronized String[] list(Context ctx) {
        File[] entries = projects(ctx);
        String[] titles = new String[entries.length];
        for (int i=0;i<entries.length;i++) titles[i] = name(entries[i]);
        return titles;
    }
    /** Called when an image is imported: previously saved projects stay untouched. */
    public static synchronized void imported(Context ctx, String sourceName) {
        prefs(ctx).edit().remove(SELECTED)
            .putString(DRAFT, sanitize(sourceName)).apply();
    }
    private static String sanitize(String input) {
        if (input == null) return "UNTITLED";
        String cleaned = input.replaceAll("(?i)\\.(png|jpe?g|webp)$","")
            .replaceAll("[^\\p{L}\\p{N} _-]"," ").trim();
        if(cleaned.length()>22)cleaned=cleaned.substring(0,22).trim();
        return cleaned.length()==0?"UNTITLED":cleaned;
    }
    public static synchronized void save(Context ctx, boolean saveAsNew) throws IOException {
        File photo = current(ctx, PHOTO);
        if(!photo.isFile()) throw new IOException("Import an image before saving a project");
        SharedPreferences p = prefs(ctx);
        final boolean duplicating = saveAsNew && p.getString(SELECTED,null)!=null;
        String selected = saveAsNew ? null : p.getString(SELECTED,null);
        File directory = selected==null?null:new File(root(ctx),selected);
        if(directory==null || !directory.isDirectory()) {
            selected = "project_" + System.currentTimeMillis() + "_" +
                Integer.toHexString((int)(Math.random()*65536));
            directory = new File(root(ctx), selected);
        }
        if(!directory.exists() && !directory.mkdirs())
            throw new IOException("Cannot create project directory");
        replace(photo,new File(directory,PHOTO));
        CollageStore.saveToProject(ctx,directory);
        File depth = current(ctx, DEPTH);
        if(depth.isFile()) replace(depth,new File(directory,DEPTH));
        else new File(directory,DEPTH).delete();
        File title = new File(directory,TITLE);
        if(!title.exists()) {
            String display=p.getString(DRAFT,"UNTITLED");
            if(duplicating) display=sanitize(display+" COPY "+(projects(ctx).length));
            try(FileOutputStream out=new FileOutputStream(title)) {
                out.write(display.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
        }
        directory.setLastModified(System.currentTimeMillis());
        p.edit().putString(SELECTED,selected).putString(DRAFT,name(directory)).apply();
    }
    public static synchronized void open(Context ctx,int index) throws IOException {
        File[] entries=projects(ctx);
        if(index<0 || index>=entries.length) throw new IOException("Project not found");
        File directory=entries[index];
        File image=new File(directory,PHOTO);
        if(!image.isFile()) throw new IOException("Missing project photograph");
        // Install the photo first; missing project depth never borrows current depth.
        replace(image,current(ctx,PHOTO));
        CollageStore.openFromProject(ctx,directory);
        File depth=new File(directory,DEPTH);
        if(depth.isFile()) replace(depth,current(ctx,DEPTH));
        else current(ctx,DEPTH).delete();
        prefs(ctx).edit().putString(SELECTED,directory.getName())
            .putString(DRAFT,name(directory)).apply();
    }
    public static synchronized String currentName(Context ctx) {
        return prefs(ctx).getString(DRAFT,"UNTITLED");
    }
}
