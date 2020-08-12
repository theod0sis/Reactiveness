package gr.aueb.reactiveness.analysis;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.searches.ReferencesSearch;

/**
 * Analyse asyncTask implementation for validity.
 *
 * @author taggelis
 */
public final class AnalyseAsyncTask {

    /**
     * Analyse if valid to refactor.
     * 1)search if forbidden method has been found. Those methods are AsyncTask.isCancelled()
     * and AsyncTask.getStatus().
     *
     * @return the boolean
     */
    public static boolean isInvalidToRefactor(final PsiClass asyncTaskClass) {
        boolean isInvalid = false;
        //search if forbidden method is called
        for (PsiReference reference : ReferencesSearch.search(asyncTaskClass)) {
            PsiElement ref = reference.getElement();
            if (ref.getContainingFile() != asyncTaskClass.getContainingFile()
                                || ref.getParent().getParent() instanceof PsiField) {
                isInvalid = true;
                break;
            }
            if ((ref.getParent() instanceof PsiNewExpression && !(ref.getParent()
                .getParent() instanceof PsiLocalVariable)) || ref.getParent() instanceof PsiReferenceExpression) {
                isInvalid = ref.getParent().getParent().getText().endsWith("isCancelled") ||
                    ref.getParent().getParent().getText().endsWith("getStatus");
                if(isInvalid) {
                    break;
                }
            }
        }
        return isInvalid;
    }
}
