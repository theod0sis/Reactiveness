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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * The type Async task refactor.
 *
 * @author taggelis
 */
public class AsyncTaskRefactor {

    private final String COMPOSITE_DISPOSABLE_IMPORT = "io.reactivex.rxjava3.disposables";

    /**
     * Refactor inner async task.
     *
     * @param factory    the factory
     * @param innerAsync the inner async
     */
    public void refactorInnerAsyncTask(PsiElementFactory factory, Map<PsiClass, PsiClass> innerAsync) {
        for (PsiClass keySet : innerAsync.keySet()) {
            new WriteCommandAction.Simple(keySet.getProject(), keySet.getContainingFile()) {
                @Override
                protected void run() throws Throwable {
                    // 0. Create CompositeDisposable to handle subscriptions
                    createCompositeDisposable(factory, keySet);
                    // 1.Move AsyncTask fields to Activity and rename them
                    // Precondition: Single async-task instance active
                    moveAsyncTaskFieldsToParentClass(keySet, innerAsync.get(keySet));
                    // 2. Extract asyncTask implementation to enclosing activity
                    extractMethods(keySet, innerAsync.get(keySet));
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
            String impQualifiedName = Objects.requireNonNull(is.getImportReference()).getQualifiedName();
            if (fullyQualifiedName.equals(impQualifiedName)) {
                return; // Already imported so nothing needed
            }
        }
        // Not imported yet so add it
        importList.add(elementFactory.createImportStatementOnDemand(fullyQualifiedName));
    }

    private void createCompositeDisposable(PsiElementFactory factory, PsiClass psiClass) {
        addImport(factory, COMPOSITE_DISPOSABLE_IMPORT, psiClass);
        PsiField compositeDisposableField = factory
            .createFieldFromText("private CompositeDisposable compositeDisposable = new CompositeDisposable();",
                psiClass);
        PsiField[] allFields = psiClass.getFields();
        GenerateMembersUtil
            .insertMembersAtOffset(psiClass, allFields[allFields.length - 1].getTextOffset() + 1,
                Collections.<GenerationInfo>singletonList(
                    new PsiGenerationInfo<>(compositeDisposableField)));
    }

    private void moveAsyncTaskFieldsToParentClass(PsiClass psiParentClass, PsiClass asyncTaskClass) {
        PsiField[] asyncFields = asyncTaskClass.getAllFields();
        for (PsiField psiField : asyncFields) {
            GenerateMembersUtil
                .insertMembersAtOffset(psiParentClass, asyncTaskClass.getTextOffset() - 1,
                    Collections.<GenerationInfo>singletonList(
                        new PsiGenerationInfo<>(psiField)));
        }
    }

    private void extractMethods(PsiClass psiParentClass, PsiClass asyncTaskClass) {
        PsiMethod[] asyncMethods = asyncTaskClass.getAllMethods();
        for (PsiMethod psiMethod : asyncMethods) {
            PsiModifierList modifierLists = psiMethod.getModifierList();
            modifierLists.setModifierProperty(PsiModifier.PRIVATE, true);
            modifierLists.setModifierProperty(PsiModifier.PROTECTED, false);
            char[] methodName =psiMethod.getName().toCharArray();
            methodName[0] = Character.toUpperCase(methodName[0]);
            psiMethod.setName("rx"+ new String(methodName));
            GenerateMembersUtil
                .insertMembersAtOffset(psiParentClass, psiParentClass.getTextOffset(),
                    Collections.<GenerationInfo>singletonList(new PsiGenerationInfo<>(psiMethod)));
        }
    }
}
