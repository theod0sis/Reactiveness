package gr.aueb.reactiveness.refactor;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.java.PsiDeclarationStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiKeywordImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import gr.aueb.reactiveness.utils.ReactivenessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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
                    // Search if onProgressUpdate and onPreExecute exist
                    boolean onProgressUpdateExist = Arrays.stream(innerAsync.get(keySet).getAllMethods())
                        .anyMatch(psiMethod -> psiMethod.getName().equals("onProgressUpdate"));
                    boolean onPreExecuteExist = Arrays.stream(innerAsync.get(keySet).getAllMethods())
                        .anyMatch(psiMethod -> psiMethod.getName().equals("onPreExecute"));
                    // 1.Move AsyncTask fields to Activity and rename them
                    // Precondition: Single async-task instance active
                    moveAsyncTaskFieldsToParentClass(keySet, innerAsync.get(keySet));
                    // 2. Extract asyncTask implementation to enclosing activity
                    extractMethods(keySet, innerAsync.get(keySet));

                    changeAsyncTaskExecuteToRx(onProgressUpdateExist, onPreExecuteExist, innerAsync.get(keySet),
                        factory);

                    generateOrUpdateOnDestroy(keySet, factory);
                    //final delete the asyncTask inner class
                    innerAsync.get(keySet).delete();
                    // change do in background emmit events on BehaviorSubject
                    if (onProgressUpdateExist) {
                        changeDoInBackgroundOnProgressUpdate(keySet, factory);
                    }
                    new ReformatCodeProcessor(keySet.getContainingFile(), false).run();
                    JavaCodeStyleManager.getInstance(keySet.getProject()).optimizeImports(keySet.getContainingFile());
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
            .insertMembersAtOffset(psiClass, allFields[0].getTextOffset(),
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
            //change visibility from protected to private
            PsiUtil.setModifierProperty(psiMethod, PsiModifier.PRIVATE, true);
            //remove override annotation
            AddAnnotationPsiFix.removePhysicalAnnotations(psiMethod, "Override");
            //change the methodName to camelCase with rx prefix
            char[] methodName = psiMethod.getName().toCharArray();
            methodName[0] = Character.toUpperCase(methodName[0]);
            psiMethod.setName("rx" + new String(methodName));

            GenerateMembersUtil
                .insertMembersAtOffset(psiParentClass, psiParentClass.getTextOffset(),
                    Collections.<GenerationInfo>singletonList(new PsiGenerationInfo<>(psiMethod)));
        }
    }

    private void initializeBehaviorSubject(final PsiMethodImpl psiMethod, final PsiElementFactory factory) {
        PsiType behaviorType = factory.createTypeFromText("BehaviorSubject<String>", psiMethod);
        PsiExpression initValue = factory.createExpressionFromText("BehaviorSubject.create()", psiMethod);
        PsiDeclarationStatement progressSubject =
            factory.createVariableDeclarationStatement("progressSubject", behaviorType, initValue);
        PsiDeclarationStatementImpl psiDeclarationStatement = (PsiDeclarationStatementImpl) psiMethod.getBody()
            .getStatements()[0].addAfter(progressSubject, psiMethod.getBody().getStatements()[0].getLastChild());

        PsiType disposable = factory.createTypeFromText("Disposable", psiMethod);
        PsiExpression disposableInitValue = factory.createExpressionFromText("progressSubject"
            + "\n.observeOn(AndroidSchedulers.mainThread())"
            + "\n.subscribe(s -> rxProgressUpdate(s))", psiMethod);
        PsiDeclarationStatement declarationStatement = factory
            .createVariableDeclarationStatement("disposal", disposable, disposableInitValue);
        PsiDeclarationStatementImpl psiDec = (PsiDeclarationStatementImpl) psiDeclarationStatement
            .addAfter(declarationStatement, declarationStatement.getLastChild());

        PsiStatement statement = factory
            .createStatementFromText("compositeDisposable.add(disposal);", psiMethod);

        psiDec.addAfter(statement, statement.getLastChild());
    }

    private void generateOrUpdateOnDestroy(PsiClass psiClass, final PsiElementFactory factory) {
        Optional<PsiMethod> doInBackground = Arrays.stream(psiClass.getMethods())
            .filter(psiMethod -> psiMethod.getName().equals("onDestroy"))
            .findFirst();
        // method onDestroy exist then update it with disposable.dispose() else create it(only for Activities).
        if (doInBackground.isPresent()) {
            PsiIfStatement ifStatement = (PsiIfStatement) factory
                .createStatementFromText("if(a){\ncompositeDisposable.dispose();\n}", null);

            PsiExpression condition = ifStatement.getCondition();
            PsiExpression expr = factory
                .createExpressionFromText("compositeDisposable != null && !compositeDisposable.isDisposed()",
                    doInBackground.get());
            if (condition != null) {
                condition.replace(expr);
            }
            doInBackground.get().addBefore(ifStatement, doInBackground.get().getBody().getLastBodyElement());
        } else if (ReactivenessUtils.findIfExtendsActivity(psiClass)) {
            PsiType voidKey = factory.createTypeFromText(PsiKeywordImpl.VOID, psiClass);
            PsiMethod onDestroyMethod = factory.createMethod("onDestroy", voidKey);
            onDestroyMethod.getModifierList().addAnnotation("Override");

            PsiStatement superStatement = factory.createStatementFromText("super.onDestroy();", onDestroyMethod);
            PsiIfStatement ifStatement = (PsiIfStatement) factory
                .createStatementFromText("if(a){\ncompositeDisposable.dispose();\n}", null);

            PsiExpression condition = ifStatement.getCondition();
            PsiExpression expr = factory
                .createExpressionFromText("compositeDisposable != null && !compositeDisposable.isDisposed()",
                    onDestroyMethod);
            if (condition != null) {
                condition.replace(expr);
            }
            PsiUtil.setModifierProperty(onDestroyMethod, PsiModifier.PROTECTED, true);
            PsiElement superElement = onDestroyMethod.getBody().add(superStatement);
            onDestroyMethod.getBody().addAfter(ifStatement, superElement);
            psiClass.add(onDestroyMethod);
        }
    }

    private void changeDoInBackgroundOnProgressUpdate(PsiClass psiClass, final PsiElementFactory factory) {

        Optional<PsiMethod> doInBackground = Arrays.stream(psiClass.getMethods())
            .filter(psiMethod -> psiMethod.getName().equals("rxDoInBackground"))
            .findFirst();

        if (doInBackground.isPresent()) {
            PsiParameter publishProgressParam = factory
                .createParameterFromText("Observer<String> publishProgress", doInBackground.get());
            doInBackground.get().getParameterList().add(publishProgressParam);
            final List<PsiReferenceExpression> expr = new ArrayList<>();
            doInBackground.get().accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
                    super.visitReferenceExpression(referenceExpression);
                    if ("publishProgress".equals(referenceExpression.getText())) {
                        expr.add(referenceExpression);
                    }
                }
            });
            PsiExpression expression = factory.createExpressionFromText("publishProgress.onNext", doInBackground.get());
            expr.forEach(ex -> ex.replace(expression));
        }
    }

    private void changeAsyncTaskExecuteToRx(final boolean onProgressUpdateExist, final boolean onPreExecuteExist,
                                            final PsiClass innerAsync, final PsiElementFactory factory) {
        List<PsiReference> executeReference = new ArrayList<>();
        List<PsiLocalVariable> localVariables = new ArrayList<>();
        List<PsiMethodCallExpression> executeDirectCalls = new ArrayList<>();
        ReferencesSearch.search(innerAsync).forEach(reference -> {
            //reference is finding the declaration two times so we will keep only the new Expression
            if (reference.getElement().getParent() instanceof PsiTypeElement) {
                return;
            }
            if (reference.getElement().getParent().getParent() instanceof PsiLocalVariable) {
                ReferencesSearch.search(reference.getElement().getParent().getParent())
                    .forEach((Consumer<PsiReference>) executeReference::add);
                localVariables.add((PsiLocalVariable) reference.getElement().getParent().getParent());
            } else if (reference.getElement().getParent().getParent().getParent() instanceof PsiMethodCallExpression) {
                executeDirectCalls.add(
                    (PsiMethodCallExpression) reference.getElement().getParent().getParent().getParent());
            }
            // collect all parent methods of the reference
            List<PsiMethodImpl> methodList = PsiTreeUtil
                .collectParents(reference.getElement(), PsiMethodImpl.class, false,
                    e -> e instanceof PsiClass);

            methodList.forEach(psiMethod -> {
                if (onProgressUpdateExist) {
                    initializeBehaviorSubject(psiMethod, factory);
                }
            });
        });

        executeReference.forEach(executeCalls -> {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) PsiTreeUtil
                .findFirstParent(executeCalls.getElement(), false, e -> e instanceof PsiMethodCallExpression);
            List<PsiMethodImpl> methods = PsiTreeUtil
                .collectParents(executeCalls.getElement(), PsiMethodImpl.class, false,
                    e -> e instanceof PsiClass);
            if (onPreExecuteExist) {
                addOnPreExecute(factory, executeCalls, methods.get(0));
            }
            generateRxCode(factory, methodCallExpression, methods.get(0));
        });
        localVariables.forEach(PsiLocalVariable::delete);

        // For new AsyncTask().execute()
        executeDirectCalls.forEach(directCalls -> {
            List<PsiMethodImpl> methods = PsiTreeUtil
                .collectParents(directCalls, PsiMethodImpl.class, false,
                    e -> e instanceof PsiClass);
            if (onPreExecuteExist) {
                addOnPreExecute(factory, directCalls.getReference(), methods.get(0));
            }
            generateRxCode(factory, directCalls, methods.get(0));
        });
    }

    private void generateRxCode(final PsiElementFactory factory, final PsiMethodCallExpression directCalls,
                                final PsiMethodImpl method) {
        PsiExpression[] arguments = directCalls.getArgumentList().getExpressions();
        final String[] s = {""};
        Arrays.stream(arguments).forEach(arg -> s[0] = s[0] + arg.getText() + ',');

        PsiStatement rxStatement = rxStatements(factory, method, s);

        PsiElement rxReplaceElement = directCalls.getParent().replace(rxStatement);
        PsiStatement statement = factory
            .createStatementFromText("compositeDisposable.add(d2);", method);
        method.addAfter(statement, rxReplaceElement);
    }

    @NotNull private PsiStatement rxStatements(final PsiElementFactory factory, final PsiMethodImpl method,
                                               final String[] s) {
        return factory.createStatementFromText(
            "Disposable d2 = Single.fromCallable(() -> doInBackground(" + (s[0] + " progressSubject") + "))\n"
                + ".subscribeOn(Schedulers.io())\n" + ".observeOn(AndroidSchedulers.mainThread())\n"
                + ".subscribe(s -> rxOnPostExecute(s));", method);
    }

    private void addOnPreExecute(final PsiElementFactory factory, final PsiReference executeCalls,
                                 final PsiMethodImpl method) {
        PsiStatement onPreExecuteStatement = factory
            .createStatementFromText("rxOnPreExecute();\n", method);
        PsiElement parent = PsiTreeUtil
            .findFirstParent(executeCalls.getElement(), false, e -> e instanceof PsiExpressionStatement);
        method.addBefore(onPreExecuteStatement, parent);
    }
}
