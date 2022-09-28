package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

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
        return Util.collectFields(targetClasses);
    }

    @Override
    protected int getCompletionItemKind() {
        return Util.FIELD_ITEM;
    }
}
