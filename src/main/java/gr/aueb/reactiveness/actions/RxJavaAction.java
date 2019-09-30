package gr.aueb.reactiveness.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.indexing.FileBasedIndex;
import gr.aueb.reactiveness.utils.Commons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

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

        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance()
            .getContainingFiles(FileTypeIndex.NAME, JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        virtualFiles.forEach(virtualFile -> {
            if ( "JAVA".equals(virtualFile.getFileType().getName())) {
                PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtualFile);
                PsiClass[] javaFileClasses = Objects.requireNonNull(psiJavaFile).getClasses();

                for (PsiClass javaFileClass : javaFileClasses) {
                    if(javaFileClass.getInnerClasses()!=null){
                        for (PsiClass innerClass : javaFileClass.getInnerClasses()) {
                            analyse(project, innerClass);

                        }
                    }
                    analyse(project, javaFileClass);
                }

            }

        });

    }

    private void analyse(final Project project, final PsiClass javaFileClass) {
        PsiReferenceList extendsList = javaFileClass.getExtendsList();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiMethod prototype = factory.createMethod("myTest", PsiType.BOOLEAN);
        PsiUtil.setModifierProperty(prototype, PsiModifier.PRIVATE, true);
        for (PsiClassType referencedType : Objects.requireNonNull(extendsList).getReferencedTypes()) {
            //check if the java class extends asynctask
            if (Commons.ASYNCTASK.equals(referencedType.getClassName())) {
                System.out.println("i came here!!");

                Collection<PsiReference> psiReferences = ReferencesSearch
                    .search(javaFileClass, GlobalSearchScope.allScope(project),false).findAll();
                psiReferences.forEach(psiReference -> {
                    PsiElement element = psiReference.resolve();
                    System.out.println("psiReference  content :" +psiReference.getCanonicalText() + "  " + psiReference.getElement()
                        + "  " + psiReference.getRangeInElement() + "  "  );
                });
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    javaFileClass.add(prototype);
                });
            }
        }
    }

    @Override
    public void update(AnActionEvent e) {

    }

}