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
import com.intellij.psi.PsiIfStatement;
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
import gr.aueb.reactiveness.utils.AsyncTaskInstance;
import gr.aueb.reactiveness.utils.Commons;
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
    private final String BEHAVIOR_SUBJECT_IMPORT = "io.reactivex.rxjava3.subjects";
    private final String ANDROID_SCHEDULERS_IMPORT = "io.reactivex.rxjava3.android.schedulers";
    private final String SINGLE_IMPORT = "io.reactivex.rxjava3.core";
    private final String SCHEDULERS_IMPORT = "io.reactivex.rxjava3.schedulers";

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
                    // create AsyncTaskInstance
                    AsyncTaskInstance instance = new AsyncTaskInstance(innerAsync.get(keySet));
                    // 1.Move AsyncTask fields to Activity and rename them
                    // Precondition: Single async-task instance active
                    moveAsyncTaskFieldsToParentClass(keySet, instance);
                    // 2. Extract asyncTask implementation to enclosing activity
                    extractMethods(keySet, instance);
                    // 3.   Create BehaviourSubject to handle progress updates
                    //      Change doInBackground() to enclosing activity that executes the task and change
                    //      Assemble observable pipeline
                    changeAsyncTaskExecuteToRx(instance.isOnProgressUpdateExist(), instance.isOnPreExecuteExist(),
                        instance, factory);
                    // 4. Dispose subscriptions on method onDestroy
                    generateOrUpdateOnDestroy(keySet, factory);
                    // 5. Change do in background emmit events on BehaviorSubject
                    if (instance.isOnProgressUpdateExist()) {
                        changeDoInBackgroundOnProgressUpdate(keySet, factory, instance.getTaskName());
                    }
                    // 6. import rx classes
                    addNecessaryImports(keySet, factory, instance.isOnProgressUpdateExist());
                    // 7. finally delete the asyncTask inner class
                    instance.getClassInstance().delete();
                    // 8. Reformat Code
                    new ReformatCodeProcessor(keySet.getContainingFile(), false).run();
                    JavaCodeStyleManager.getInstance(keySet.getProject()).optimizeImports(keySet.getContainingFile());
                }
            }.execute();
        }
    }


    private void createCompositeDisposable(PsiElementFactory factory, PsiClass psiClass) {
        ReactivenessUtils.addImport(factory, COMPOSITE_DISPOSABLE_IMPORT, psiClass);
        PsiField compositeDisposableField = factory
            .createFieldFromText("private CompositeDisposable compositeDisposable = new CompositeDisposable();",
                psiClass);
        PsiField[] allFields = psiClass.getFields();
        GenerateMembersUtil
            .insertMembersAtOffset(psiClass, allFields[0].getTextOffset(),
                Collections.<GenerationInfo>singletonList(
                    new PsiGenerationInfo<>(compositeDisposableField)));
    }

    private void moveAsyncTaskFieldsToParentClass(PsiClass psiParentClass, AsyncTaskInstance asyncTaskInstance) {
        for (PsiField psiField : asyncTaskInstance.getAllFields()) {
            GenerateMembersUtil
                .insertMembersAtOffset(psiParentClass, asyncTaskInstance.getTextOffset() - 1,
                    Collections.<GenerationInfo>singletonList(
                        new PsiGenerationInfo<>(psiField)));
        }
    }

    private void extractMethods(PsiClass psiParentClass, AsyncTaskInstance asyncTaskClass) {
        PsiMethod[] asyncMethods = asyncTaskClass.getAllMethods();
        for (PsiMethod psiMethod : asyncMethods) {
            //change visibility from protected to private
            PsiUtil.setModifierProperty(psiMethod, PsiModifier.PRIVATE, true);
            //remove override annotation
            AddAnnotationPsiFix.removePhysicalAnnotations(psiMethod, "java.lang.Override");
            //change the methodName to camelCase with rx prefix
            if (Commons.DO_IN_BACKGROUND.equals(psiMethod.getName())) {
                char[] taskName = asyncTaskClass.getTaskName().toCharArray();
                taskName[0] = Character.toUpperCase(taskName[0]);
                psiMethod.setName("do" + new String(taskName));
            } else if (Commons.ASYNC_TASK_METHODS.contains(psiMethod.getName())) {
                psiMethod.setName(asyncTaskClass.getTaskName() + psiMethod.getName().substring(2));
            } else {
                char[] methodName = psiMethod.getName().toCharArray();
                methodName[0] = Character.toUpperCase(methodName[0]);
                psiMethod.setName("rx" + new String(methodName));
            }
            GenerateMembersUtil
                .insertMembersAtOffset(psiParentClass, psiParentClass.getTextOffset(),
                    Collections.<GenerationInfo>singletonList(new PsiGenerationInfo<>(psiMethod)));
        }
    }

    private void initializeBehaviorSubject(final PsiMethodImpl psiMethod, final PsiElementFactory factory,
                                           final String taskName) {
        PsiType behaviorType = factory.createTypeFromText("BehaviorSubject<String>", psiMethod);
        PsiExpression initValue = factory.createExpressionFromText("BehaviorSubject.create()", psiMethod);
        PsiDeclarationStatement progressSubject =
            factory.createVariableDeclarationStatement(Commons.PROGRESS_SUBJECT, behaviorType, initValue);
        PsiDeclarationStatementImpl psiDeclarationStatement = (PsiDeclarationStatementImpl) Objects
            .requireNonNull(psiMethod.getBody())
            .getStatements()[0].addAfter(progressSubject, psiMethod.getBody().getStatements()[0].getLastChild());

        PsiType disposable = factory.createTypeFromText("Disposable", psiMethod);
        PsiExpression disposableInitValue = factory.createExpressionFromText(Commons.PROGRESS_SUBJECT
            + "\n.observeOn(AndroidSchedulers.mainThread())"
            + "\n.subscribe(s -> " + taskName + "ProgressUpdate(s))", psiMethod);
        PsiDeclarationStatement declarationStatement = factory
            .createVariableDeclarationStatement("disposal", disposable, disposableInitValue);
        PsiDeclarationStatementImpl psiDec = (PsiDeclarationStatementImpl) psiDeclarationStatement
            .addAfter(declarationStatement, declarationStatement.getLastChild());

        PsiStatement statement = factory
            .createStatementFromText("compositeDisposable.add(disposal);", psiMethod);

        psiDec.addAfter(statement, statement.getLastChild());
    }

    private void addNecessaryImports(final PsiClass psiParentClass, final PsiElementFactory factory,
                                     final boolean onProgressUpdateExist) {
        if (onProgressUpdateExist) {
            ReactivenessUtils.addImport(factory, ANDROID_SCHEDULERS_IMPORT, psiParentClass);
            ReactivenessUtils.addImport(factory, BEHAVIOR_SUBJECT_IMPORT, psiParentClass);
        }
        ReactivenessUtils.addImport(factory, SINGLE_IMPORT, psiParentClass);
        ReactivenessUtils.addImport(factory, SCHEDULERS_IMPORT, psiParentClass);
    }

    private void generateOrUpdateOnDestroy(PsiClass psiClass, final PsiElementFactory factory) {
        Optional<PsiMethod> onDestroy = Arrays.stream(psiClass.getMethods())
            .filter(psiMethod -> psiMethod.getName().equals("onDestroy"))
            .findFirst();
        // method onDestroy exist then update it with disposable.dispose() else create it(only for Activities).
        if (onDestroy.isPresent()) {
            PsiIfStatement ifStatement = (PsiIfStatement) factory
                .createStatementFromText("if(a){\ncompositeDisposable.dispose();\n}", null);

            PsiExpression condition = ifStatement.getCondition();
            PsiExpression expr = factory
                .createExpressionFromText("compositeDisposable != null && !compositeDisposable.isDisposed()",
                    onDestroy.get());
            if (condition != null) {
                condition.replace(expr);
            }
            onDestroy.get()
                .addBefore(ifStatement, Objects.requireNonNull(onDestroy.get().getBody()).getLastBodyElement());
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

    private void changeDoInBackgroundOnProgressUpdate(PsiClass psiClass, final PsiElementFactory factory,
                                                      final String taskName) {

        Optional<PsiMethod> doInBackground = Arrays.stream(psiClass.getMethods())
            .filter(psiMethod -> psiMethod.getName().equalsIgnoreCase("do" + taskName))
            .findFirst();

        if (doInBackground.isPresent()) {
            PsiParameter publishProgressParam = factory
                .createParameterFromText("Observer<String> publishProgress", doInBackground.get());
            if (doInBackground.get().getParameterList().isEmpty()) {
                doInBackground.get().getParameterList().add(publishProgressParam);
            } else {
                doInBackground.get().getParameterList()
                    .addBefore(publishProgressParam,doInBackground.get().getParameterList().getParameter(0));
            }
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
                                            final AsyncTaskInstance innerAsync, final PsiElementFactory factory) {
        List<PsiReference> executeReference = new ArrayList<>();
        List<PsiLocalVariable> localVariables = new ArrayList<>();
        List<PsiMethodCallExpression> executeDirectCalls = new ArrayList<>();
        ReferencesSearch.search(innerAsync.getClassInstance()).forEach(reference -> {
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
                    initializeBehaviorSubject(psiMethod, factory, innerAsync.getTaskName());
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
                addOnPreExecute(factory, executeCalls, methods.get(0), innerAsync.getTaskName());
            }
            generateRxCode(factory, methodCallExpression, methods.get(0), onProgressUpdateExist,
                innerAsync.getTaskName());
        });
        localVariables.forEach(PsiLocalVariable::delete);

        // For new AsyncTask().execute()
        executeDirectCalls.forEach(directCalls -> {
            List<PsiMethodImpl> methods = PsiTreeUtil
                .collectParents(directCalls, PsiMethodImpl.class, false,
                    e -> e instanceof PsiClass);
            if (onPreExecuteExist) {
                addOnPreExecute(factory, directCalls.getReference(), methods.get(0), innerAsync.getTaskName());
            }
            generateRxCode(factory, directCalls, methods.get(0), onProgressUpdateExist, innerAsync.getTaskName());
        });
    }

    private void generateRxCode(final PsiElementFactory factory, final PsiMethodCallExpression directCalls,
                                final PsiMethodImpl method, final boolean onProgressUpdateExist,
                                final String taskName) {
        PsiExpression[] arguments = directCalls.getArgumentList().getExpressions();
        final String[] s = {""};
        Arrays.stream(arguments).forEach(arg -> s[0] = s[0] + arg.getText() + ",");
        //remove last coma
        s[0] = s[0].substring(0, s[0].length() - 1);
        PsiStatement rxStatement = rxStatements(factory, method, s, onProgressUpdateExist, taskName);

        PsiElement rxReplaceElement = directCalls.getParent().replace(rxStatement);
        PsiStatement statement = factory
            .createStatementFromText("compositeDisposable.add(d2);", method);
        method.addAfter(statement, rxReplaceElement);
    }

    @NotNull private PsiStatement rxStatements(final PsiElementFactory factory, final PsiMethodImpl method,
                                               final String[] s, final boolean onProgressUpdateExist,
                                               final String taskName) {
        // taskName is camelcase and starts with lower letter
        char[] name = taskName.toCharArray();
        name[0] = Character.toUpperCase(name[0]);
        return factory.createStatementFromText(
            "Disposable d2 = Single.fromCallable(() -> do" + new String(name) + "(" + (onProgressUpdateExist
                ? Commons.PROGRESS_SUBJECT + "," : "") + s[0] + "))\n"
                + ".subscribeOn(Schedulers.io())\n" + ".observeOn(AndroidSchedulers.mainThread())\n"
                + ".subscribe(s -> " + taskName + "PostExecute(s));", method);
    }

    private void addOnPreExecute(final PsiElementFactory factory, final PsiReference executeCalls,
                                 final PsiMethodImpl method, final String taskName) {
        PsiStatement onPreExecuteStatement = factory
            .createStatementFromText(taskName + "PreExecute();\n", method);
        PsiElement parent = PsiTreeUtil
            .findFirstParent(executeCalls.getElement(), false, e -> e instanceof PsiExpressionStatement);
        method.addBefore(onPreExecuteStatement, parent);
    }
}
