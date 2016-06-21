package org.rust.lang.core.psi.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementVisitor

abstract class RustRecursiveElementVisitor : RustElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
    }

}

abstract class RustComputingVisitor<R: Any>: RustRecursiveElementVisitor() {
    private var result: R? = null

    fun compute(element: PsiElement): R {
        element.accept(this)
        return checkNotNull(result) {
            "Element $element was unhandled" +
                "\n${element.containingFile.virtualFile.path}" +
                "\n${element.text}"
        }
    }

    protected fun go(block: () -> R) {
        result = block()
    }
}
