package net.sourceforge.plantuml.text;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import net.sourceforge.plantuml.eclipse.utils.PlantumlConstants;

public class TextEditorDiagramTextProvider extends AbstractDiagramTextProvider {

	public TextEditorDiagramTextProvider() {
		setEditorType(ITextEditor.class);
	}

	private boolean startIsRegexp = true, endIsRegexp = true;
	private boolean includeStart = true, includeEnd = true;

	public boolean supportsSelection(ISelection selection) {
		return selection instanceof ITextSelection;
	}

	private final static String newline = "\n";

	@Override
	protected String getDiagramText(IEditorPart editorPart, IEditorInput editorInput, ISelection selection) {
		StringBuilder lines = getDiagramTextLines(editorPart, editorInput, selection);
		return (lines != null ? getDiagramText(lines) : null);
	}

	protected StringBuilder getDiagramTextLines(IEditorPart editorPart, IEditorInput editorInput, ISelection selection) {
		ITextEditor textEditor = (ITextEditor) editorPart;
		IDocument document = textEditor.getDocumentProvider().getDocument(editorInput);
		int selectionStart = ((ITextSelection) textEditor.getSelectionProvider().getSelection()).getOffset();
		FindReplaceDocumentAdapter finder = new FindReplaceDocumentAdapter(document);
		try {
			IRegion start = finder.find(selectionStart, PlantumlConstants.START_UML, true, true, (! startIsRegexp), startIsRegexp);
			IRegion end = finder.find(selectionStart, PlantumlConstants.END_UML, true, true, (! endIsRegexp), endIsRegexp);
			if (start == null || (end != null && end.getOffset() < start.getOffset())) {
				// use a slightly larger selection offset, in case the cursor is within startuml
				start = finder.find(Math.min(selectionStart + PlantumlConstants.START_UML.length(), document.getLength()), PlantumlConstants.START_UML, false, true, (! startIsRegexp), startIsRegexp);
			}
			if (start != null) {
				end = finder.find(start.getOffset(), PlantumlConstants.END_UML, true, true, (! endIsRegexp), endIsRegexp);
				if (end != null) {
					int startOffset = start.getOffset(), endOffset = end.getOffset() + end.getLength();
					int startLine = document.getLineOfOffset(startOffset);
//					String linePrefix = document.get(startLinePos, startOffset - startLinePos).trim();
					StringBuilder result = new StringBuilder();
					int maxLine = Math.min(document.getLineOfOffset(endOffset) + (includeEnd ? 1 : 0), document.getNumberOfLines());
					for (int lineNum = startLine + (includeStart ? 0 : 1); lineNum < maxLine; lineNum++) {
						String line = document.get(document.getLineOffset(lineNum), document.getLineLength(lineNum)).trim();
						result.append(line);
						if (! line.endsWith(newline)) {
							result.append(newline);
						}
					}
					return result;
				}
			}
		} catch (BadLocationException e) {
		}
		return null;
	}

	public String getDiagramText(CharSequence lines) {
		return getDiagramText(new StringBuilder(lines.toString()));
	}

	protected String getDiagramText(StringBuilder lines) {
		int start = Math.max(lines.indexOf(PlantumlConstants.START_UML), 0), end = Math.min(lines.lastIndexOf(PlantumlConstants.END_UML) + PlantumlConstants.END_UML.length(), lines.length());
		String linePrefix = lines.substring(0, start).trim();
		StringBuilder result = new StringBuilder(lines.length());
		while (start < end) {
			int lineEnd = lines.indexOf("\n", start);
			if (lineEnd > end) {
				break;
			} else if (lineEnd < 0) {
				lineEnd = lines.length();
			}
			String line = lines.substring(start, lineEnd).trim();
			if (line.startsWith(linePrefix)) {
				line = line.substring(linePrefix.length()).trim();
			}
			result.append(line);
			result.append("\n");
			start = lineEnd + 1;
		}
		return result.toString().trim();
	}
}
