package com.exiledradio.wildupdatebackport.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Small helpers shared by the patches. Every member is matched by its MCP name (the dev environment) or its SRG name
 * (a real instance), and by descriptor, never by position alone.
 */
final class Asm {

    static final Logger LOG = LogManager.getLogger("Wild Update Backport Core");

    private Asm() {
    }

    static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    /**
     * Writes a class back without recomputing frames: computing them would load classes from inside a transformer.
     * The patches here only put a static call between a value and its use, which changes no frame.
     */
    static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static MethodNode method(ClassNode node, String mcp, String srg, String desc) {
        for (MethodNode m : node.methods) {
            if ((mcp.equals(m.name) || srg.equals(m.name)) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }
}
