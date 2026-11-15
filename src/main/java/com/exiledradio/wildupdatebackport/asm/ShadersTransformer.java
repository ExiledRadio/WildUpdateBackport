package com.exiledradio.wildupdatebackport.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patches P10 and P11 ({@code docs/PATCHES.md}), stream W: Darkness for OptiFine shader packs. Modern OptiFine (1.19+)
 * gives shader packs two uniforms, {@code darknessFactor} and {@code darknessLightFactor}, also usable as variables in
 * {@code shaders.properties} custom uniforms. OptiFine 1.12.2 HD U G5 has neither: a pack that reads them gets 0 and
 * OptiFine logs "Model variable not found: darknessFactor". These two patches define them as 1.19's OptiFine does, next
 * to {@code blindness}:
 *
 * <ul>
 *   <li>P10, {@code net.optifine.shaders.Shaders}: two {@code ShaderUniform1f} fields made in its static init
 *   ({@code shaderUniforms.make1f("darknessFactor")}, after {@code blindness}'s), two {@code float} fields set at the
 *   start of {@code beginRender} from {@code DarknessShaders} (once a frame), and {@code useProgram} sets both uniforms
 *   on every program after {@code blindness}.</li>
 *   <li>P11, {@code net.optifine.shaders.uniform.ShaderParameterFloat}: two enum constants, {@code DARKNESS_FACTOR}
 *   and {@code DARKNESS_LIGHT_FACTOR}, made as {@code BLINDNESS} is (on the uniform, so {@code eval} reads its value),
 *   appended to {@code $VALUES}. {@code ShaderExpressionResolver} registers every constant by name, so custom uniforms
 *   find them. Only applied once P10 is.</li>
 * </ul>
 *
 * Each site is matched strictly (exactly one hit); a class of another shape (another OptiFine build) is left alone with
 * a log line. Nothing here jumps or adds a local; the code inserted at {@code beginRender}'s head reads its float
 * parameter before any frame.
 */
public final class ShadersTransformer implements IClassTransformer {

    static final String SHADERS = "net.optifine.shaders.Shaders";
    static final String PARAMETER = "net.optifine.shaders.uniform.ShaderParameterFloat";
    private static final String SHADERS_I = "net/optifine/shaders/Shaders";
    private static final String PARAMETER_I = "net/optifine/shaders/uniform/ShaderParameterFloat";
    private static final String UNIFORMS = "net/optifine/shaders/uniform/ShaderUniforms";
    private static final String UNIFORM1F = "net/optifine/shaders/uniform/ShaderUniform1f";
    private static final String UNIFORM1F_D = "L" + UNIFORM1F + ";";
    private static final String HOOKS = "com/exiledradio/wildupdatebackport/warden/client/DarknessShaders";

    /** 1.19 OptiFine's names: uniform field, value field, uniform name, enum constant. */
    private static final String[][] NAMES = {
            {"uniform_darknessFactor", "darknessFactor", "darknessFactor", "DARKNESS_FACTOR", "factor"},
            {"uniform_darknessLightFactor", "darknessLightFactor", "darknessLightFactor", "DARKNESS_LIGHT_FACTOR", "lightFactor"},
    };

    /** Set once the class has been patched; P11 and the tests read them. */
    public static volatile boolean appliedP10;
    public static volatile boolean appliedP11;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        if (SHADERS.equals(transformedName)) {
            byte[] out = shaders(basicClass);
            appliedP10 = out != basicClass;
            return out;
        }
        if (PARAMETER.equals(transformedName)) {
            if (!appliedP10) {
                Asm.LOG.warn("P11 OptiFine Darkness variables: {} loaded before a patched {}; patch skipped.", PARAMETER, SHADERS);
                return basicClass;
            }
            byte[] out = parameters(basicClass);
            appliedP11 = out != basicClass;
            return out;
        }
        return basicClass;
    }

    /** Public for the offline check. @return the patched class, or the same array if not patched */
    public static byte[] shaders(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        String why = shadersWhy(node);
        if (why != null) {
            Asm.LOG.warn("P10 OptiFine Darkness uniforms: {} is not of the expected shape ({}); patch skipped, shader packs get no Darkness.",
                    SHADERS, why);
            return bytes;
        }
        Asm.LOG.info("P10 OptiFine Darkness uniforms: patched {}.", SHADERS);
        return Asm.write(node);
    }

    /** Public for the offline check. @return the patched class, or the same array if not patched */
    public static byte[] parameters(byte[] bytes) {
        ClassNode node = Asm.read(bytes);
        String why = parametersWhy(node);
        if (why != null) {
            Asm.LOG.warn("P11 OptiFine Darkness variables: {} is not of the expected shape ({}); patch skipped, custom uniforms get no Darkness.",
                    PARAMETER, why);
            return bytes;
        }
        Asm.LOG.info("P11 OptiFine Darkness variables: patched {}.", PARAMETER);
        return Asm.write(node);
    }

    // --- P10 ---------------------------------------------------------------------------------------------------------

    /** Patches node in place. @return null when patched, else why not (node then unchanged) */
    private static String shadersWhy(ClassNode node) {
        for (String[] n : NAMES) {
            if (field(node, n[0]) != null || field(node, n[1]) != null) {
                return "already has " + n[0] + " or " + n[1];
            }
        }
        FieldNode shaderUniforms = field(node, "shaderUniforms");
        FieldNode uniformBlindness = field(node, "uniform_blindness");
        FieldNode blindness = field(node, "blindness");
        if (shaderUniforms == null || !("L" + UNIFORMS + ";").equals(shaderUniforms.desc)
                || uniformBlindness == null || !UNIFORM1F_D.equals(uniformBlindness.desc)
                || blindness == null || !"F".equals(blindness.desc)) {
            return "no shaderUniforms, uniform_blindness or blindness field";
        }
        if (Asm.method(node, "setProgramUniform1f", "setProgramUniform1f", "(" + UNIFORM1F_D + "F)V") == null) {
            return "no setProgramUniform1f";
        }
        // <clinit>: getstatic shaderUniforms; ldc "blindness"; invokevirtual make1f; putstatic uniform_blindness
        MethodNode clinit = Asm.method(node, "<clinit>", "<clinit>", "()V");
        AbstractInsnNode made = null;
        int hits = 0;
        if (clinit != null) {
            for (AbstractInsnNode i = clinit.instructions.getFirst(); i != null; i = i.getNext()) {
                if (isField(i, Opcodes.PUTSTATIC, "uniform_blindness")) {
                    AbstractInsnNode call = i.getPrevious();
                    AbstractInsnNode name = call == null ? null : call.getPrevious();
                    AbstractInsnNode owner = name == null ? null : name.getPrevious();
                    hits++;
                    if (isMethod(call, Opcodes.INVOKEVIRTUAL, UNIFORMS, "make1f", "(Ljava/lang/String;)" + UNIFORM1F_D)
                            && name instanceof LdcInsnNode && "blindness".equals(((LdcInsnNode) name).cst)
                            && isField(owner, Opcodes.GETSTATIC, "shaderUniforms")) {
                        made = i;
                    }
                }
            }
        }
        if (hits != 1 || made == null) {
            return "<clinit>: " + hits + " stores of uniform_blindness";
        }
        // useProgram: getstatic uniform_blindness; getstatic blindness; invokestatic setProgramUniform1f
        MethodNode use = Asm.method(node, "useProgram", "useProgram", "(Lnet/optifine/shaders/Program;)V");
        AbstractInsnNode set = null;
        hits = 0;
        if (use != null) {
            for (AbstractInsnNode i = use.instructions.getFirst(); i != null; i = i.getNext()) {
                if (isField(i, Opcodes.GETSTATIC, "uniform_blindness")) {
                    AbstractInsnNode value = i.getNext();
                    AbstractInsnNode call = value == null ? null : value.getNext();
                    hits++;
                    if (isField(value, Opcodes.GETSTATIC, "blindness")
                            && isMethod(call, Opcodes.INVOKESTATIC, SHADERS_I, "setProgramUniform1f", "(" + UNIFORM1F_D + "F)V")) {
                        set = call;
                    }
                }
            }
        }
        if (hits != 1 || set == null) {
            return "useProgram: " + hits + " reads of uniform_blindness";
        }
        // beginRender(Minecraft, float, long)
        MethodNode begin = null;
        for (MethodNode m : node.methods) {
            Type[] args = Type.getArgumentTypes(m.desc);
            if ("beginRender".equals(m.name) && (m.access & Opcodes.ACC_STATIC) != 0 && args.length == 3
                    && args[0].getSort() == Type.OBJECT && args[1] == Type.FLOAT_TYPE && args[2] == Type.LONG_TYPE
                    && Type.getReturnType(m.desc) == Type.VOID_TYPE) {
                if (begin != null) {
                    return "two beginRender methods";
                }
                begin = m;
            }
        }
        if (begin == null || begin.instructions.size() == 0) {
            return "no beginRender(Minecraft, float, long)";
        }

        InsnList init = new InsnList();
        InsnList uniforms = new InsnList();
        InsnList values = new InsnList();
        for (String[] n : NAMES) {
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, n[0], UNIFORM1F_D, null, null));
            node.fields.add(new FieldNode(Opcodes.ACC_STATIC, n[1], "F", null, null));
            init.add(new FieldInsnNode(Opcodes.GETSTATIC, SHADERS_I, "shaderUniforms", "L" + UNIFORMS + ";"));
            init.add(new LdcInsnNode(n[2]));
            init.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, UNIFORMS, "make1f", "(Ljava/lang/String;)" + UNIFORM1F_D, false));
            init.add(new FieldInsnNode(Opcodes.PUTSTATIC, SHADERS_I, n[0], UNIFORM1F_D));
            uniforms.add(new FieldInsnNode(Opcodes.GETSTATIC, SHADERS_I, n[0], UNIFORM1F_D));
            uniforms.add(new FieldInsnNode(Opcodes.GETSTATIC, SHADERS_I, n[1], "F"));
            uniforms.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SHADERS_I, "setProgramUniform1f", "(" + UNIFORM1F_D + "F)V", false));
            values.add(new VarInsnNode(Opcodes.FLOAD, 1));
            values.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, n[4], "(F)F", false));
            values.add(new FieldInsnNode(Opcodes.PUTSTATIC, SHADERS_I, n[1], "F"));
        }
        clinit.instructions.insert(made, init);
        use.instructions.insert(set, uniforms);
        begin.instructions.insert(values);
        return null;
    }

    // --- P11 ---------------------------------------------------------------------------------------------------------

    private static String parametersWhy(ClassNode node) {
        String self = "L" + PARAMETER_I + ";";
        String array = "[" + self;
        String ctor = "(Ljava/lang/String;ILnet/optifine/shaders/uniform/ShaderUniformBase;)V";
        if (Asm.method(node, "<init>", "<init>", ctor) == null) {
            return "no (ShaderUniformBase) constructor";
        }
        FieldNode values = field(node, "$VALUES");
        if (values == null || !array.equals(values.desc)) {
            return "no $VALUES";
        }
        int constants = 0;
        for (FieldNode f : node.fields) {
            if ((f.access & Opcodes.ACC_ENUM) != 0) {
                constants++;
            }
            for (String[] n : NAMES) {
                if (n[3].equals(f.name)) {
                    return "already has " + n[3];
                }
            }
        }
        FieldNode blindness = field(node, "BLINDNESS");
        if (blindness == null || !self.equals(blindness.desc) || constants < 1 || constants > 120) {
            return "no BLINDNESS (" + constants + " constants)";
        }
        // <clinit> ends: putstatic $VALUES; return (one store)
        MethodNode clinit = Asm.method(node, "<clinit>", "<clinit>", "()V");
        AbstractInsnNode store = null;
        int hits = 0;
        int returns = 0;
        if (clinit != null) {
            for (AbstractInsnNode i = clinit.instructions.getFirst(); i != null; i = i.getNext()) {
                if (isField(i, Opcodes.PUTSTATIC, "$VALUES")) {
                    hits++;
                    AbstractInsnNode next = i.getNext();
                    while (next != null && next.getOpcode() < 0) {
                        next = next.getNext();
                    }
                    if (next != null && next.getOpcode() == Opcodes.RETURN) {
                        store = i;
                    }
                }
                if (i.getOpcode() == Opcodes.RETURN) {
                    returns++;
                }
            }
        }
        if (hits != 1 || returns != 1 || store == null) {
            return "<clinit>: " + hits + " stores of $VALUES, " + returns + " returns";
        }

        InsnList add = new InsnList();
        for (int k = 0; k < NAMES.length; k++) {
            String[] n = NAMES[k];
            node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, n[3], self, null, null));
            add.add(new TypeInsnNode(Opcodes.NEW, PARAMETER_I));
            add.add(new InsnNode(Opcodes.DUP));
            add.add(new LdcInsnNode(n[3]));
            add.add(new IntInsnNode(Opcodes.SIPUSH, constants + k));
            add.add(new FieldInsnNode(Opcodes.GETSTATIC, SHADERS_I, n[0], UNIFORM1F_D));
            add.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, PARAMETER_I, "<init>", ctor, false));
            add.add(new FieldInsnNode(Opcodes.PUTSTATIC, PARAMETER_I, n[3], self));
        }
        // $VALUES = Arrays.copyOf($VALUES, n + 2), then the two new constants at its end.
        add.add(new FieldInsnNode(Opcodes.GETSTATIC, PARAMETER_I, "$VALUES", array));
        add.add(new IntInsnNode(Opcodes.SIPUSH, constants + NAMES.length));
        add.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false));
        add.add(new TypeInsnNode(Opcodes.CHECKCAST, array));
        for (int k = 0; k < NAMES.length; k++) {
            add.add(new InsnNode(Opcodes.DUP));
            add.add(new IntInsnNode(Opcodes.SIPUSH, constants + k));
            add.add(new FieldInsnNode(Opcodes.GETSTATIC, PARAMETER_I, NAMES[k][3], self));
            add.add(new InsnNode(Opcodes.AASTORE));
        }
        add.add(new FieldInsnNode(Opcodes.PUTSTATIC, PARAMETER_I, "$VALUES", array));
        clinit.instructions.insert(store, add);
        return null;
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private static FieldNode field(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static boolean isField(AbstractInsnNode i, int opcode, String name) {
        return i instanceof FieldInsnNode && i.getOpcode() == opcode && name.equals(((FieldInsnNode) i).name);
    }

    private static boolean isMethod(AbstractInsnNode i, int opcode, String owner, String name, String desc) {
        if (!(i instanceof MethodInsnNode) || i.getOpcode() != opcode) {
            return false;
        }
        MethodInsnNode m = (MethodInsnNode) i;
        return owner.equals(m.owner) && name.equals(m.name) && desc.equals(m.desc);
    }
}
