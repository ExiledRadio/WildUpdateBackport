package com.exiledradio.wildupdatebackport.selftest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Has the JVM itself verify a patched class against Forge's SRG jar ({@code srgBin.jar}), which keeps Mojang's
 * ProGuard stack-map frames. ASM's analyzer computes its own frames and so cannot see a load of a local those frames
 * mark TOP (a VerifyError only in the real game); the JVM's type-checking verifier reads the frames and does.
 *
 * <p>The class is defined in its own loader over the SRG jar and this run's class path (no parent), so every other
 * Minecraft class it meets is the SRG jar's.
 */
final class FrameCheck {

    private FrameCheck() {
    }

    /** @return null when {@code bytes} verify as {@code target}, else the VerifyError's first line */
    static String verify(File srgJar, final String target, final byte[] bytes) {
        URLClassLoader loader = null;
        try {
            loader = new URLClassLoader(urls(srgJar), null) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.equals(target)) {
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    return super.findClass(name);
                }
            };
            try {
                // Initializing links, and linking verifies. A failure after verification (a static initializer
                // wanting a running game) is not the verifier's.
                Class.forName(target, true, loader);
            } catch (VerifyError e) {
                return e.getMessage().split("\n")[0];
            } catch (Throwable ignored) {
                // Verified; only initialization failed.
            }
            return null;
        } catch (MalformedURLException e) {
            return "bad class path: " + e;
        } finally {
            if (loader != null) {
                try {
                    loader.close();
                } catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    private static URL[] urls(File srgJar) throws MalformedURLException {
        List<URL> out = new ArrayList<URL>();
        out.add(srgJar.toURI().toURL());
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                out.add(new File(entry).toURI().toURL());
            }
        }
        return out.toArray(new URL[0]);
    }
}
