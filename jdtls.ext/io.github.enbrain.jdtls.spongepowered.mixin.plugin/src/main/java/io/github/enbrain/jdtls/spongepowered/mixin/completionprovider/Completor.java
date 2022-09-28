package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.List;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;

public interface Completor {
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException;
}
