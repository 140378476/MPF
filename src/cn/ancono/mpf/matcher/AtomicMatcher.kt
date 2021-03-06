package cn.ancono.mpf.matcher

import cn.ancono.mpf.core.CombinedNode
import cn.ancono.mpf.core.Node


/*
 * Created by liyicheng at 2020-04-11 19:50
 */
/**
 * @author liyicheng
 */
interface AtomicMatcher<T : Node<T>, R : Any > : Matcher<T, R> {
}

interface CombinedMatcher<T : Node<T>, R  : Any> : Matcher<T, R> {}

open class UnorderedMatcher<T : Node<T>, R : Any>(
    val type: Class<out CombinedNode<*>>,
    open val children: List<Matcher<T, R>>,
    open val fallback: Matcher<T, R>
) : CombinedMatcher<T, R> {
    override fun match(x: T, previousResult: R?): List<R> {
        if (x !is CombinedNode<*> || !type.isInstance(x)) {
            return  emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        val cb = x as CombinedNode<T>
        return MatcherUtil.unorderedMatch(cb, children, fallback, previousResult)
    }
}


open class OrderedMatcher<T : Node<T>, R : Any>(
    val type: Class<out CombinedNode<*>>,
    open val children: List<Matcher<T, R>>
) : CombinedMatcher<T, R> {
    override fun match(x: T, previousResult: R?): List<R> {
        if (x !is CombinedNode<*> || !type.isInstance(x)) {
            return emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        val cb = x as CombinedNode<T>
        return MatcherUtil.orderedMatch(cb, children, previousResult)
    }
}

open class UnaryMatcher<T : Node<T>, R : Any>(
    val type: Class<out CombinedNode<*>>,
    val subMatcher: Matcher<T, R>
) : CombinedMatcher<T,R>{
    override fun match(x: T, previousResult: R?): List<R>{
        if (x !is CombinedNode<*> || !type.isInstance(x)) {
            return emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        val cb = x as CombinedNode<T>
        return subMatcher.match(cb.children.first(),previousResult)
    }
}



