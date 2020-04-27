package gr.aueb.reactiveness.refactor;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;

import java.util.Collections;
import java.util.Map;

/**
 * @author taggelis
 */
public class AsyncTaskRefactor {

    private final String COMPOSITE_DISPOSABLE_IMPORT = "io.reactivex.rxjava3.disposables";

    public void refactorInnerAsyncTask(PsiElementFactory factory, Map<PsiClass, PsiClass> innerAsync) {
        for (PsiClass keySet : innerAsync.keySet()) {
            new WriteCommandAction.Simple(keySet.getProject(), keySet.getContainingFile()) {
                @Override
                protected void run() throws Throwable {
                    addImport(factory, COMPOSITE_DISPOSABLE_IMPORT, keySet);
                    PsiField compositeDisposableField = factory
                        .createFieldFromText("CompositeDisposable compositeDisposable = new CompositeDisposable();",
                            keySet);
                    PsiField[] allFields = keySet.getFields();
                    GenerateMembersUtil
                        .insertMembersAtOffset(keySet, allFields[allFields.length - 1].getTextOffset() + 1,
                            Collections.<GenerationInfo>singletonList(
                                new PsiGenerationInfo<>(compositeDisposableField)));
                }
            }.execute();
        }
    }

    private void addImport(PsiElementFactory elementFactory, String fullyQualifiedName, PsiClass psiClass) {
        final PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return;
        }

        final PsiJavaFile javaFile = (PsiJavaFile) file;

        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        // Check if already imported
        for (PsiImportStatementBase is : importList.getAllImportStatements()) {
            String impQualifiedName = is.getImportReference().getQualifiedName();
            if (fullyQualifiedName.equals(impQualifiedName)) {
                return; // Already imported so nothing neede
            }
        }
        // Not imported yet so add it
        importList.add(elementFactory.createImportStatementOnDemand(fullyQualifiedName));
    }
}
