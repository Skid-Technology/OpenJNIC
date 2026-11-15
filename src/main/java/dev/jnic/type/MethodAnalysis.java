package dev.jnic.type;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

public class MethodAnalysis {
    private Frame<StackValueType>[] frames;

    @SuppressWarnings("unchecked")
    public MethodAnalysis(ClassNode classNode, MethodNode methodNode) throws AnalyzerException {
        if (classNode != null && methodNode != null && methodNode.instructions != null && methodNode.instructions.size() > 0) {
            try {
                Analyzer<StackValueType> analyzer = new Analyzer<>(StackTypeInterpreter.INSTANCE);
                this.frames = analyzer.analyze(classNode.name, methodNode);
            } catch (Exception ignored) {
                this.frames = new Frame[methodNode.instructions.size()];
            }
        } else {
            this.frames = new Frame[0];
        }
    }

    public Frame<StackValueType> getFrame(int index) {
        if (this.frames == null || index < 0 || index >= this.frames.length) {
            return null;
        }
        return this.frames[index];
    }

    public Frame<StackValueType>[] getFrames() {
        return this.frames;
    }
}
