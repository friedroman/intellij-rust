package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

abstract class RustReferenceBase<T : RustCompositeElement>(
    element: T
) : PsiReferenceBase<T>(element)
  , RustReference {

    abstract val T.referenceAnchor: PsiElement

    abstract fun resolveVerbose(): RustResolveEngine.ResolveResult

    final override fun resolve(): RustNamedElement? =
        resolveVerbose().let {
            when (it) {
                is RustResolveEngine.ResolveResult.Resolved -> it.element
                else -> null
            }
        }

    // enforce not nullability
    final override fun getRangeInElement(): TextRange = super.getRangeInElement()

    final override fun calculateDefaultRangeInElement(): TextRange {
        check(element.referenceAnchor.parent === element)
        return element.referenceAnchor.parentRelativeRange
    }
}
