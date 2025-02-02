package io.goodforgod.dummymapper.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;

/**
 * @author Anton Kurako (GoodforGod)
 * @since 19.4.2020
 */
public class IdeaUtils {

    private IdeaUtils() {}

    public static Optional<PsiJavaFile> getFileFromAction(AnActionEvent event) {
        final PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
        return (psiFile instanceof PsiJavaFile)
                ? Optional.of(((PsiJavaFile) psiFile))
                : Optional.empty();
    }

    public static Optional<PsiClass> getPsiClassFromAction(AnActionEvent event) {
        final PsiElement psiClass = event.getData(CommonDataKeys.PSI_ELEMENT);
        return (psiClass instanceof PsiClass)
                ? Optional.of(((PsiClass) psiClass))
                : Optional.empty();
    }

    public static void copyToClipboard(String content) {
        final StringSelection selection = new StringSelection(content);
        CopyPasteManager.getInstance().setContents(selection);
    }
}
