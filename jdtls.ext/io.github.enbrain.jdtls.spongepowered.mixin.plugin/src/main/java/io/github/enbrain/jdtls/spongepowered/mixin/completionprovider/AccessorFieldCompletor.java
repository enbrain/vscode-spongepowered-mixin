package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;

import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @Accessor(value = "...")}.
 */
public class AccessorFieldCompletor extends AccessorOrInvokerMemberCompletor {
    @Override
    protected String getAnnotationClassName() {
        return Util.ACCESSOR_ANNOTATION;
    }

    @Override
    protected List<String> collectMembers(List<IType> targetClasses) throws JavaModelException {
        Set<String> result = new HashSet<>();

        for (IType target : targetClasses) {
            if (target instanceof JavaElement element) {
                for (Object object : element.getChildrenOfType(8)) {
                    if (object instanceof IField f) {
                        result.add(f.getElementName());
                    }
                }
            }
        }

        return List.copyOf(result);
    }

    @Override
    protected int getCompletionItemKind() {
        return Util.FIELD_ITEM;
    }
}
