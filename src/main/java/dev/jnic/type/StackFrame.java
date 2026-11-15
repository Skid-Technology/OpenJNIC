package dev.jnic.type;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

public class StackFrame {
    private final Frame<BasicValue> inputFrame;
    private final Frame<BasicValue> outputFrame;
    private final MethodAnalysis analysis;

    public StackFrame(Frame<BasicValue> inputFrame, Frame<BasicValue> outputFrame, MethodAnalysis analysis) {
        this.inputFrame = inputFrame;
        this.outputFrame = outputFrame;
        this.analysis = analysis;
    }

    public int getInputStackSize() {
        return this.inputFrame != null ? this.inputFrame.getStackSize() : 0;
    }

    public int getOutputStackSize() {
        return this.outputFrame != null ? this.outputFrame.getStackSize() : 0;
    }

    public String topSlotChar(int offsetFromTop) {
        Type t = this.getInputStackType(offsetFromTop);
        return String.valueOf(JniTypeUtils.slotChar(t));
    }

    public Type getInputStackType(int offsetFromTop) {
        if (this.inputFrame == null) {
            return Type.getType("Ljava/lang/Object;");
        }
        int size = this.inputFrame.getStackSize();
        int idx = size - 1 - offsetFromTop;
        if (idx < 0 || idx >= size) {
            return Type.getType("Ljava/lang/Object;");
        }
        BasicValue val = this.inputFrame.getStack(idx);
        if (val == null || val.getType() == null) {
            return Type.getType("Ljava/lang/Object;");
        }
        return val.getType();
    }

    public Type getOutputStackType(int offsetFromTop) {
        if (this.outputFrame == null) {
            return Type.getType("Ljava/lang/Object;");
        }
        int size = this.outputFrame.getStackSize();
        int idx = size - 1 - offsetFromTop;
        if (idx < 0 || idx >= size) {
            return Type.getType("Ljava/lang/Object;");
        }
        BasicValue val = this.outputFrame.getStack(idx);
        if (val == null || val.getType() == null) {
            return Type.getType("Ljava/lang/Object;");
        }
        return val.getType();
    }

    public boolean isPossiblyNull(int offsetFromTop) {
        return true;
    }

    public Type topStackType(int offsetFromTop) {
        return this.getInputStackType(offsetFromTop);
    }

    public int topStackSize(int offsetFromTop) {
        if (this.inputFrame != null) {
            int size = this.inputFrame.getStackSize();
            int idx = size - 1 - offsetFromTop;
            if (idx >= 0 && idx < size) {
                BasicValue val = this.inputFrame.getStack(idx);
                if (val != null) {
                    return val.getSize();
                }
            }
        }
        Type t = this.getInputStackType(offsetFromTop);
        if (t != null && (t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)) {
            return 2;
        }
        return 1;
    }
}
