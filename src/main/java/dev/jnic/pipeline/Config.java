package dev.jnic.pipeline;

import dev.jnic.type.MethodMatcher;
import dev.jnic.util.Platform;
import java.util.List;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(name = "jnic")
public class Config {
    @Element(name = "options", required = false)
    private Options options;
    @ElementList(name = "targets", entry = "target", required = false)
    private List<Platform> targets;
    @Element(name = "includeAnnotation", required = false)
    private String includeAnnotation;
    @Element(name = "excludeAnnotation", required = false)
    private String excludeAnnotation;
    @ElementList(name = "include", required = false)
    private List<MethodMatcher> include;
    @ElementList(name = "exclude", required = false)
    private List<MethodMatcher> exclude;

    public Options getOptions() {
        return this.options;
    }

    public List<Platform> getTargets() {
        return this.targets;
    }

    public boolean isIncluded(ClassNode classNode, MethodNode methodNode) {
        if (this.includeAnnotation != null && !this.includeAnnotation.isEmpty()
                && (this.hasIncludeAnnotation(classNode) || this.hasIncludeAnnotation(methodNode))) {
            return true;
        }
        if (this.excludeAnnotation != null && !this.excludeAnnotation.isEmpty()
                && (this.hasExcludeAnnotation(classNode) || this.hasExcludeAnnotation(methodNode))) {
            return false;
        }
        boolean included = this.include == null || this.include.isEmpty();
        if (!included) {
            for (MethodMatcher matcher : this.include) {
                if (matcher.matches(classNode.name, methodNode.name, methodNode.desc)) {
                    included = true;
                    break;
                }
            }
        }
        if (!included) {
            return false;
        }
        if (this.exclude != null) {
            for (MethodMatcher matcher : this.exclude) {
                if (matcher.matches(classNode.name, methodNode.name, methodNode.desc)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String target) {
        if (annotations == null || target == null || target.isEmpty()) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation == null || annotation.desc == null) {
                continue;
            }
            String name = annotation.desc.replace('/', '.');
            if (name.length() > 1 && name.charAt(0) == 'L' && name.endsWith(";")) {
                name = name.substring(1, name.length() - 1);
            }
            if (name.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIncludeAnnotation(MethodNode methodNode) {
        return hasAnnotation(methodNode.visibleAnnotations, this.includeAnnotation)
                || hasAnnotation(methodNode.invisibleAnnotations, this.includeAnnotation);
    }

    private boolean hasExcludeAnnotation(MethodNode methodNode) {
        return hasAnnotation(methodNode.visibleAnnotations, this.excludeAnnotation)
                || hasAnnotation(methodNode.invisibleAnnotations, this.excludeAnnotation);
    }

    private boolean hasIncludeAnnotation(ClassNode classNode) {
        return hasAnnotation(classNode.visibleAnnotations, this.includeAnnotation)
                || hasAnnotation(classNode.invisibleAnnotations, this.includeAnnotation);
    }

    private boolean hasExcludeAnnotation(ClassNode classNode) {
        return hasAnnotation(classNode.visibleAnnotations, this.excludeAnnotation)
                || hasAnnotation(classNode.invisibleAnnotations, this.excludeAnnotation);
    }
}
