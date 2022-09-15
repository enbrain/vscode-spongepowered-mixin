import * as vscode from 'vscode';

export default class MixinCompletionItemProvider implements vscode.CompletionItemProvider {
	async provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext): Promise<vscode.CompletionItem[] | vscode.CompletionList<vscode.CompletionItem>> {
		try {
			const completionItems: vscode.CompletionItem[] = await vscode.commands.executeCommand(
				"java.execute.workspaceCommand", "spongepowered.mixin.completion",
				`${document.uri.scheme}://${document.uri.authority}${document.uri.path}${document.uri.query}${document.uri.fragment}`,
				position.line, position.character);
			return new vscode.CompletionList(completionItems, false);
		} catch (e) {
			console.error(e);
		}

		return new vscode.CompletionList([], false);
	}
}