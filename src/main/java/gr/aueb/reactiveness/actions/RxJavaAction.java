package gr.aueb.reactiveness.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import gr.aueb.reactiveness.utils.Commons;
import gr.aueb.reactiveness.utils.ReactivenessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The main action that refactors asyncTask to RxJava2.
 */
public class RxJavaAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();

        if (project == null || project.isDisposed()) {
            return;
        }

        // retrieve all virtualFiles from project
        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance()
            .getContainingFiles(FileTypeIndex.NAME, JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        List<PsiClass> validClasses = new ArrayList<>();
        List<PsiClass> validInnerClasses = new ArrayList<>();
        virtualFiles.forEach(virtualFile -> {

            //check if the file has .java extension
            if (JavaFileType.DEFAULT_EXTENSION.equalsIgnoreCase(virtualFile.getFileType().getName())) {

                PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtualFile);

                // every Java file has to have one main Class
                if(Objects.requireNonNull(psiJavaFile).getClasses().length == 0){
                    return;
                }
                PsiClass javaFileClass = Objects.requireNonNull(psiJavaFile).getClasses()[0];

                if (ReactivenessUtils.findIfExtendsAsyncTask(javaFileClass) && analyse(javaFileClass)) {
                    validClasses.add(javaFileClass);

                }

                //search also for inner classes
                for (PsiClass javaInnerClass : javaFileClass.getInnerClasses()) {
                    if (ReactivenessUtils.findIfExtendsAsyncTask(javaInnerClass) && analyse(javaInnerClass)) {
                        validInnerClasses.add(javaInnerClass);
                    }
                }
            }
        });
        if (!validInnerClasses.isEmpty()) {
            refactorInnerClass(validInnerClasses.get(0));
        }
    }

    private boolean analyse(final PsiClass javaFileClass) {
        PsiMethod[] methods = javaFileClass.getAllMethods();
        Optional<PsiMethod> backgroundMethod = Arrays.stream(methods)
            .filter(method -> Commons.DO_IN_BACKGROUND.equalsIgnoreCase(method.getName())).findFirst();
        return backgroundMethod.isPresent();
    }

    private void refactorInnerClass(final PsiClass javaFileClass) {

        PsiMethod[] methods = javaFileClass.getAllMethods();
        Optional<PsiMethod> backgroundMethod = Arrays.stream(methods)
            .filter(method -> Commons.DO_IN_BACKGROUND.equalsIgnoreCase(method.getName())).findFirst();

        backgroundMethod.get().getReturnType();
    }

    //PsiReferenceList extendsList = javaFileClass.getExtendsList();
    //PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    //PsiMethod prototype = factory.createMethod("myTest", PsiType.BOOLEAN);
    //PsiUtil.setModifierProperty(prototype, PsiModifier.PRIVATE, true);
    //for (PsiClassType referencedType : Objects.requireNonNull(extendsList).getReferencedTypes()) {
    //    //check if the java class extends asynctask
    //    if (Commons.ASYNCTASK.equals(referencedType.getClassName())) {
    //        System.out.println("i came here!!");
    //
    //        Collection<PsiReference> psiReferences = ReferencesSearch
    //            .search(javaFileClass, GlobalSearchScope.allScope(project), false).findAll();
    //        psiReferences.forEach(psiReference -> {
    //            PsiElement element = psiReference.resolve();
    //            System.out.println(
    //                "psiReference  content :" + psiReference.getCanonicalText() + "  " + psiReference.getElement()
    //                    + "  " + psiReference.getRangeInElement() + "  ");
    //        });
    //        WriteCommandAction.runWriteCommandAction(project, () -> {
    //            javaFileClass.add(prototype);
    //        });
    //    }
    //}
    @Override
    public void update(AnActionEvent e) {

    }

}