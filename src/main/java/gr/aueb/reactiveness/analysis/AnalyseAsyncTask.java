package gr.aueb.reactiveness.analysis;

import com.intellij.psi.PsiClass;
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
    public static boolean analyseIfValidToRefactor(final PsiClass asyncTaskClass){
        //search if forbidden method is called
        ReferencesSearch.search(asyncTaskClass).forEach(reference -> {
            reference.getCanonicalText();
        });
        return false;
    }
}
