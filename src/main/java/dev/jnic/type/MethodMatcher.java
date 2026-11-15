package dev.jnic.type;

import dev.jnic.pipeline.TranslateException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

@Root(name = "match")
public class MethodMatcher {
    @Attribute(name = "className", required = false)
    private String className;
    private Pattern classPattern;
    @Attribute(name = "methodName", required = false)
    private String methodName;
    private Pattern methodPattern;
    @Attribute(name = "methodDesc", required = false)
    private String methodDesc;
    private Pattern descPattern;

    @Commit
    private void compile() throws TranslateException {
        try {
            this.classPattern = this.className == null || this.className.isEmpty() ? null : Pattern.compile(this.className);
            this.methodPattern = this.methodName == null || this.methodName.isEmpty() ? null : Pattern.compile(this.methodName);
            this.descPattern = this.methodDesc == null || this.methodDesc.isEmpty() ? null : Pattern.compile(this.methodDesc);
        } catch (PatternSyntaxException e) {
            throw new TranslateException(e.getMessage(), e);
        }
    }

    public boolean matches(String className, String methodName, String methodDesc) {
        if (this.classPattern != null && (className == null || !this.classPattern.matcher(className).matches())) {
            return false;
        }
        if (this.methodPattern != null && (methodName == null || !this.methodPattern.matcher(methodName).matches())) {
            return false;
        }
        if (this.descPattern != null && (methodDesc == null || !this.descPattern.matcher(methodDesc).matches())) {
            return false;
        }
        return true;
    }
}
