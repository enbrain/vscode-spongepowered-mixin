package io.github.enbrain.jdtls.spongepowered.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;

import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.AccessorFieldCompletor;
import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.Completor;
import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.InjectionPointTargetCompletor;
import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.InjectionPointTypeCompletor;
import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.InjectorMethodCompletor;
import io.github.enbrain.jdtls.spongepowered.mixin.completionprovider.InvokerMethodCompletor;

public final class CompletionHandler {
    private CompletionHandler() {
    }

    private static final List<Completor> COMPLETORS = List.of(
            new AccessorFieldCompletor(),
            new InjectionPointTargetCompletor(),
            new InjectionPointTypeCompletor(),
            new InjectorMethodCompletor(),
            new InvokerMethodCompletor());

    public static List<CompletionItem> complete(String uri, int line, int column) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);

        if (unit != null) {
            int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
            ASTNode root = parse(unit);
            ASTNode current = NodeFinder.perform(root, offset, 1);

            for (Completor completor : COMPLETORS) {
                result.addAll(completor.complete(root, current));
            }
        }

        return result;
    }

    private static ASTNode parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.JLS18);

        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, Util.JAVA_VERSION);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, Util.JAVA_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, Util.JAVA_VERSION);

        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);

        JavaCore.setComplianceOptions(Util.JAVA_VERSION, options);
        parser.setCompilerOptions(options);

        return parser.createAST(new NullProgressMonitor());
    }
}
