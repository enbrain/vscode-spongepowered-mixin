package io.github.enbrain.jdtls.spongepowered.mixin;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

public class DelegateCommandHandler implements IDelegateCommandHandler {
    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        if (Objects.equals(commandId, "spongepowered.mixin.completion")) {
            String uri = (String) arguments.get(0);
            int line = (int) (double) arguments.get(1);
            int column = (int) (double) arguments.get(2);
            return CompletionHandler.complete(uri, line, column);
        }
        return null;
    }
}
