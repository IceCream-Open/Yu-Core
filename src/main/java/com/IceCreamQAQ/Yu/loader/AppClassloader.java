package com.IceCreamQAQ.Yu.loader;

import com.IceCreamQAQ.Yu.hook.YuHook;
import com.IceCreamQAQ.Yu.loader.enchant.EnchantManager;
import com.IceCreamQAQ.Yu.loader.transformer.ClassTransformer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import sun.misc.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AppClassloader extends ClassLoader {

    private final List<ClassTransformer> transformers = new ArrayList<>();

    public AppClassloader(ClassLoader parent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        super(parent);
        YuHook.init(this);
        EnchantManager.init(this);

        for (String s : transformerList) {
            transformers.add((ClassTransformer) loadClass(s, true, false).newInstance());
        }
    }

    private static final List<String> blackList = new ArrayList<>();
    private static final List<String> transformerList = new ArrayList<>();

    public static void registerBackList(List<String> packageName) {
        blackList.addAll(packageName);
    }

    public static void registerTransformerList(String packageName) {
        transformerList.add(packageName);
    }

    @SneakyThrows
    public Class<?> loadClass(String name, boolean resolve) {
        return loadClass(name, resolve, true);
    }

    @SneakyThrows
    public Class<?> loadClass(String name, boolean resolve, boolean enhance) {
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        if (isBlackListClass(name)) c = this.getParent().loadClass(name);

        if (c == null) if (enhance) c = loadAppClass(name);

        if (null == c) {
            return super.loadClass(name, resolve);
        } else {
            if (resolve) this.resolveClass(c);
            return c;
        }
    }

    private Class<?> loadAppClass(String name) throws IOException, ClassNotFoundException {
        log.debug(String.format("Load Class: %s.", name));

        val path = name.replace(".", "/") + ".class";

        val url = this.getParent().getResource(path);
        if (url == null) throw new ClassNotFoundException(name);
        val uc = url.openConnection();
        val resource = new Resource() {
            @Override
            public String getName() {
                return path;
            }

            @Override
            public URL getURL() {
                return url;
            }

            @Override
            public URL getCodeSourceURL() {
                return url;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return uc.getInputStream();
            }

            @Override
            public int getContentLength() throws IOException {
                return uc.getContentLength();
            }
        };
        var bytes = resource.getBytes();
//        if (name.equals("cn/hutool/core/util/ArrayUtil".replace("/", "."))) {
//            for (int i = 0; i < 48772; i++) {
//                System.out.println(i + ": " + bytes[i]);
//            }
//        }
        for (ClassTransformer transformer : transformers) {
            bytes = transformer.transform(bytes, name);
        }

        bytes = EnchantManager.checkClass(bytes);

//        bytes = YuHook.checkClass(name, bytes);

        return defineClass(name, bytes, 0, bytes.length);
    }

    public boolean isBlackListClass(String name) {
        val b = name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("kotlin")
                || name.startsWith("com.google.")
                || name.startsWith("org.apache.")
                || name.startsWith("sun.")
                || name.startsWith("com.sun.")
                || name.startsWith("net.sf.ehcache")
                || name.startsWith("com.IceCreamQAQ.Yu.annotation.")
                || name.startsWith("com.IceCreamQAQ.Yu.hook.")
                || name.startsWith("com.IceCreamQAQ.Yu.loader.enchant.")
                || name.startsWith("ch.qos.logback.core.")
                || name.startsWith("org.xml")
                || name.startsWith("org.slf4j.")
                || name.startsWith("org.hibernate")
                || name.startsWith("org.jboss");
        if (b) return true;
        for (String s : blackList) {
            if (name.startsWith(s)) return true;
        }
        return false;
    }
}
