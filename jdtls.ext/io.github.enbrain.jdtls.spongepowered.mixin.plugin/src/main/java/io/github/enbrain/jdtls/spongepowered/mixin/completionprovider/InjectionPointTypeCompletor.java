package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;
import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @At(value = "...")}.
 */
public class InjectionPointTypeCompletor implements Completor {
    @Override
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation annotation = Util.getAnnotationFromMember(current.getParent(), "value");
            if (annotation != null) {
                ITypeBinding annotationType = annotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();

                    if (annotationName.equals(Util.INJECTION_POINT_ANNOTATION)) {
                        for (String injectionPointType : Util.INJECTION_POINT_TYPES) {
                            result.add(new CompletionItem(injectionPointType, Util.VALUE_ITEM));
                        }
                    }
                }
            }
        }

        return result;
    }
}
