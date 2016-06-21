package org.rust.lang.core.types.visitors

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.visitors.RustComputingVisitor
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedTupleType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.type

object RustTypificationEngine {

    fun typifyType(type: RustTypeElement): RustUnresolvedType {
        val v = RustTypeTypificationVisitor()
        type.accept(v)
        return v.inferred
    }

    fun typifyExpr(expr: RustExprElement): RustType = RustExprTypificationVisitor().compute(expr)

    fun typifyItem(item: RustItemElement): RustType {
        val v = RustItemTypificationVisitor()
        item.accept(v)
        return v.inferred
    }

    fun typify(named: RustNamedElement): RustType {
        return when (named) {
            is RustItemElement -> typifyItem(named)

            is RustSelfArgumentElement -> deviseSelfType(named)

            is RustPatBindingElement -> deviseBoundPatType(named)

            else -> RustUnknownType
        }
    }

    /**
     * NOTA BENE: That's far from complete
     */
    private fun deviseBoundPatType(binding: RustPatBindingElement): RustType {
        //TODO: probably want something more precise than `getTopmostParentOfType` here
        val pattern = PsiTreeUtil.getTopmostParentOfType(binding, RustPatElement::class.java) ?: return RustUnknownType
        val parent = pattern.parent
        val type = when (parent) {
            is RustLetDeclElement ->
                // use type ascription, if present or fallback to the type of the initializer expression
                parent.type?.resolvedType ?: parent.expr?.resolvedType

            is RustParameterElement -> parent.type?.resolvedType
            else -> null
        } ?: return RustUnknownType

        return RustTypeInferenceEngine.inferPatBindingTypeFrom(binding, pattern, type)
    }

    /**
     * Devises type for the given (implicit) self-argument
     */
    private fun deviseSelfType(self: RustSelfArgumentElement): RustType =
        self.parentOfType<RustImplItemElement>()?.type?.resolvedType ?: RustUnknownType
}

private open class RustTypificationVisitorBase<T: Any> : RustRecursiveElementVisitor() {

    protected lateinit var cur: T

    val inferred: T
        get() = cur

}

private class RustExprTypificationVisitor : RustComputingVisitor<RustType>() {

    override fun visitElement(element: PsiElement) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustExprElement` hierarchy!")
    }

    override fun visitExpr(o: RustExprElement) = go {
        RustUnknownType
    }

    override fun visitPathExpr(o: RustPathExprElement) = go {
        o.path.reference.resolve()?.let { RustTypificationEngine.typify(it) } ?: RustUnknownType
    }

    override fun visitStructExpr(o: RustStructExprElement) = go {
        o.path.reference.resolve() .let { it as? RustStructItemElement }
                                  ?.let { RustStructType(it) } ?: RustUnknownType
    }

    override fun visitTupleExpr(o: RustTupleExprElement) = go {
        RustTupleType(o.exprList.map { RustTypificationEngine.typifyExpr(it) })
    }

    override fun visitUnitExpr(o: RustUnitExprElement) = go {
        RustUnitType
    }

    override fun visitCallExpr(o: RustCallExprElement) = go {
        val calleeType = o.expr.resolvedType
        if (calleeType is RustFunctionType)
            calleeType.retType
        else
            RustUnknownType
    }

    override fun visitMethodCallExpr(o: RustMethodCallExprElement) = go {
        val ref = o.reference!!
        val method = ref.resolve()
        if (method is RustImplMethodMemberElement)
            method.retType?.type?.resolvedType ?: RustUnknownType
        else
            RustUnknownType
    }
}

private class RustItemTypificationVisitor : RustTypificationVisitorBase<RustType>() {

    override fun visitElement(element: PsiElement) {
        check(element is RustItemElement) {
           "Panic! Should not be used with anything except the inheritors of `RustItemElement` hierarchy!"
        }

        cur = RustUnknownType
    }

    override fun visitStructItem(o: RustStructItemElement) {
        cur = RustStructType(o)
    }

    override fun visitFnItem(o: RustFnItemElement) {
        cur = RustFunctionType(
            o.parameters?.let { params ->
                params.parameterList.map { it.type?.resolvedType ?: RustUnknownType }
            } ?: emptyList(),
            o.retType?.let { it.type?.resolvedType ?: RustUnitType } ?: RustUnknownType
        )
    }
}

private class RustTypeTypificationVisitor : RustTypificationVisitorBase<RustUnresolvedType>() {

    override fun visitElement(element: PsiElement) {
        throw UnsupportedOperationException("Panic! Should not be used with anything except the inheritors of `RustTypeElement` hierarchy!")
    }

    override fun visitType(o: RustTypeElement) {
        cur = RustUnknownType
    }

    override fun visitTupleType(o: RustTupleTypeElement) {
        cur = RustUnresolvedTupleType(o.typeList.map { it.type })
    }

    override fun visitPathType(o: RustPathTypeElement) {
        cur = o.path?.let { RustUnresolvedPathType(it) } ?: RustUnknownType
    }

}
