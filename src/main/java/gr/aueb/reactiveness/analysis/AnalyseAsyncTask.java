package gr.aueb.reactiveness.analysis;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.searches.ReferencesSearch;

import java.util.concurrent.atomic.AtomicBoolean;

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
        AtomicBoolean isInvalid = new AtomicBoolean(false);
        //search if forbidden method is called
        ReferencesSearch.search(asyncTaskClass).forEach(reference -> {
            PsiElement ref = reference.getElement();
            if ((ref.getParent() instanceof PsiNewExpression && !(ref.getParent()
                .getParent() instanceof PsiLocalVariable)) || ref.getParent() instanceof PsiReferenceExpression) {
                isInvalid.set(ref.getParent().getParent().getText().endsWith("isCancelled") ||
                    ref.getParent().getParent().getText().endsWith("getStatus"));
            }
        });
        return isInvalid.get();
    }
}
