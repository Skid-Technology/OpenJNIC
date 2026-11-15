package dev.jnic.type;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

public enum StackValueType implements Value {
    REFERENCE(1),
    NULL(1),
    NON_NULL(1),
    PRIMITIVE(1),
    WIDE(2);

    private final int size;

    StackValueType(int size) {
        this.size = size;
    }

    public static StackValueType fromType(Type type) {
        if (type == null) {
            return REFERENCE;
        }
        int sort = type.getSort();
        if (sort == Type.VOID) {
            return null;
        }
        if (sort == Type.LONG || sort == Type.DOUBLE) {
            return WIDE;
        }
        if (sort == Type.ARRAY || sort == Type.OBJECT) {
            return REFERENCE;
        }
        return PRIMITIVE;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    public StackValueType merge(StackValueType other) {
        if (other == null || this == other) {
            return this;
        }
        if (this.size == 2 || other.size == 2) {
            return WIDE;
        }
        if ((this == NON_NULL || this == NULL || this == REFERENCE)
                && (other == NON_NULL || other == NULL || other == REFERENCE)) {
            return REFERENCE;
        }
        return PRIMITIVE;
    }
}
