package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;
import io.github.enbrain.jdtls.spongepowered.mixin.Util;

public abstract class AccessorOrInvokerMemberCompletor implements Completor {
    @Override
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation annotation = Util.getAnnotationFromMember(current.getParent(), "value");
            if (annotation != null) {
                ITypeBinding annotationType = annotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();
                    if (annotationName.equals(this.getAnnotationClassName())) {
                        List<IType> targetClasses = Util.getTargetClasses(root, annotation);
                        for (String member : this.collectMembers(targetClasses)) {
                            result.add(new CompletionItem(member, this.getCompletionItemKind()));
                        }
                    }
                }
            }
        }

        return result;
    }

    protected abstract String getAnnotationClassName();

    protected abstract List<String> collectMembers(List<IType> targetClasses) throws JavaModelException;

    protected abstract int getCompletionItemKind();
}
