
import * as vscode from 'vscode';
import MixinCompletionItemProvider from './MixinCompletionItemProvider';

export async function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.languages.registerCompletionItemProvider({ scheme: 'file', language: 'java' }, new MixinCompletionItemProvider()));
}

export function deactivate() { }
