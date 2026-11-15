import com.exiledradio.wildupdatebackport.asm.ShadersTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Offline check of patches P10 and P11 (OptiFine Darkness uniforms) against an OptiFine jar: both patch, the new
 * fields and calls are there, every method of both patched classes passes ASM's stack verifier, and a frame check
 * reads the stack map frames as the JVM does (a local read after a frame must be held by that frame; ProGuard drops
 * dead locals from OptiFine's frames). The unpatched classes go through the frame check too, to tell a pre-existing
 * oddity from one the patch made. Exit status 1 on any failure.
 *
 * <pre>    tools/darkness/check.sh &lt;OptiFine jar&gt;</pre>
 */
public final class OptiFineCheck {

    private static int errors;

    public static void main(String[] args) throws Exception {
        ZipFile jar = new ZipFile(args[0]);
        byte[] shaders = read(jar, "net/optifine/shaders/Shaders.class");
        byte[] parameters = read(jar, "net/optifine/shaders/uniform/ShaderParameterFloat.class");
        frames(shaders, "unpatched ");
        frames(parameters, "unpatched ");

        byte[] s = ShadersTransformer.shaders(shaders);
        check(s != shaders, "P10 patched Shaders");
        byte[] p = ShadersTransformer.parameters(parameters);
        check(p != parameters, "P11 patched ShaderParameterFloat");
        if (s != shaders) {
            ClassNode n = node(s);
            check(count(n, "<clinit>", Opcodes.PUTSTATIC, "uniform_darknessFactor") == 1, "Shaders.<clinit> makes uniform_darknessFactor");
            check(count(n, "<clinit>", Opcodes.PUTSTATIC, "uniform_darknessLightFactor") == 1, "Shaders.<clinit> makes uniform_darknessLightFactor");
            check(count(n, "useProgram", Opcodes.GETSTATIC, "uniform_darknessFactor") == 1, "useProgram sets darknessFactor");
            check(count(n, "useProgram", Opcodes.GETSTATIC, "uniform_darknessLightFactor") == 1, "useProgram sets darknessLightFactor");
            check(count(n, "beginRender", Opcodes.PUTSTATIC, "darknessFactor") == 1, "beginRender stores darknessFactor");
            check(count(n, "beginRender", Opcodes.PUTSTATIC, "darknessLightFactor") == 1, "beginRender stores darknessLightFactor");
            verify(s);
            check(ShadersTransformer.shaders(s) == s, "P10 a second time is skipped");
        }
        if (p != parameters) {
            ClassNode n = node(p);
            check(count(n, "<clinit>", Opcodes.PUTSTATIC, "DARKNESS_FACTOR") == 1, "ShaderParameterFloat.DARKNESS_FACTOR made");
            check(count(n, "<clinit>", Opcodes.PUTSTATIC, "DARKNESS_LIGHT_FACTOR") == 1, "ShaderParameterFloat.DARKNESS_LIGHT_FACTOR made");
            check(count(n, "<clinit>", Opcodes.PUTSTATIC, "$VALUES") == 2, "$VALUES rebuilt");
            verify(p);
            check(ShadersTransformer.parameters(p) == p, "P11 a second time is skipped");
        }
        System.out.println(errors == 0 ? "OK" : errors + " FAILED");
        System.exit(errors == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "   ok   " : "   FAIL ") + what);
        if (!ok) {
            errors++;
        }
    }

    private static int count(ClassNode n, String method, int opcode, String field) {
        int c = 0;
        for (MethodNode m : n.methods) {
            if (!m.name.equals(method)) {
                continue;
            }
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof FieldInsnNode && i.getOpcode() == opcode && ((FieldInsnNode) i).name.equals(field)) {
                    c++;
                }
            }
        }
        return c;
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void verify(byte[] bytes) {
        ClassNode node = node(bytes);
        int before = errors;
        for (MethodNode m : node.methods) {
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (AnalyzerException e) {
                System.out.println("   VERIFY " + node.name + "." + m.name + m.desc + ": " + e.getMessage());
                errors++;
            }
        }
        frames(bytes, "");
        check(errors == before, node.name + ": verifier and frame check, " + node.methods.size() + " methods");
    }

    /** As C&C's tools/height/PatchCheck.frames. */
    private static void frames(byte[] bytes, String what) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        for (MethodNode m : node.methods) {
            if (m.instructions.size() == 0) {
                continue;
            }
            Set<Integer> live = new HashSet<Integer>();
            int slot = 0;
            if ((m.access & Opcodes.ACC_STATIC) == 0) {
                live.add(slot++);
            }
            for (org.objectweb.asm.Type t : org.objectweb.asm.Type.getArgumentTypes(m.desc)) {
                live.add(slot);
                if (t.getSize() == 2) {
                    live.add(slot + 1);
                }
                slot += t.getSize();
            }
            boolean needFrame = false;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof FrameNode) {
                    FrameNode f = (FrameNode) i;
                    live.clear();
                    int s = 0;
                    if (f.local != null) {
                        for (Object o : f.local) {
                            boolean wide = o == Opcodes.LONG || o == Opcodes.DOUBLE;
                            if (o != Opcodes.TOP) {
                                live.add(s);
                                if (wide) {
                                    live.add(s + 1);
                                }
                            }
                            s += wide ? 2 : 1;
                        }
                    }
                    needFrame = false;
                    continue;
                }
                int op = i.getOpcode();
                if (op < 0) {
                    continue;
                }
                String at = what + node.name + "." + m.name + m.desc + " at instruction " + m.instructions.indexOf(i);
                if (needFrame) {
                    System.out.println("   FRAME " + at + ": code after an unconditional jump has no frame");
                    errors++;
                    needFrame = false;
                }
                if (i instanceof VarInsnNode || i instanceof IincInsnNode) {
                    int var = i instanceof VarInsnNode ? ((VarInsnNode) i).var : ((IincInsnNode) i).var;
                    boolean store = op >= Opcodes.ISTORE && op <= Opcodes.ASTORE;
                    if (store) {
                        live.add(var);
                        if (op == Opcodes.LSTORE || op == Opcodes.DSTORE) {
                            live.add(var + 1);
                        }
                    } else if (!live.contains(var)) {
                        System.out.println("   FRAME " + at + ": reads local " + var + ", which the frame before it does not hold");
                        errors++;
                    }
                }
                needFrame = op == Opcodes.GOTO || op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)
                        || op == Opcodes.TABLESWITCH || op == Opcodes.LOOKUPSWITCH;
            }
        }
    }

    private static byte[] read(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        InputStream in = zip.getInputStream(entry);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
