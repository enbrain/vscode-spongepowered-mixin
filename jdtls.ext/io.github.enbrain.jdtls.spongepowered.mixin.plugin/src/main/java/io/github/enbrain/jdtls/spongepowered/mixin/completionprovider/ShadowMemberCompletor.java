package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;
import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @Shadow(aliases = "...")}.
 */
public class ShadowMemberCompletor implements Completor {
    @Override
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        ASTNode memberParent = getMemberParentNode(current);
        if (memberParent != null) {
            Annotation annotation = Util.getAnnotationFromMember(memberParent, "aliases");
            if (annotation != null) {
                ITypeBinding annotationType = annotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();
                    if (annotationName.equals(Util.SHADOW_ANNOTATION)) {
                        List<IType> targetClasses = Util.getTargetClasses(root, annotation);
                        for (String method : Util.collectMethods(targetClasses)) {
                            result.add(new CompletionItem(method, Util.METHOD_ITEM));
                        }
                        for (String field : Util.collectFields(targetClasses)) {
                            result.add(new CompletionItem(field, Util.FIELD_ITEM));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static ASTNode getMemberParentNode(ASTNode node) {
        if (node instanceof StringLiteral) {
            ASTNode literalParent = node.getParent();
            if (literalParent instanceof ArrayInitializer array) {
                return array.getParent();
            }

            return literalParent;
        }

        return null;
    }
}
