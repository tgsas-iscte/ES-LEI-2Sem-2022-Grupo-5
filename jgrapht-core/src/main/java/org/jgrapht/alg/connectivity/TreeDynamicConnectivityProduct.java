package org.jgrapht.alg.connectivity;


import org.jgrapht.alg.connectivity.TreeDynamicConnectivity.Node;
import org.jgrapht.alg.connectivity.TreeDynamicConnectivity.Arc;
import org.jgrapht.util.*;

public class TreeDynamicConnectivityProduct {
	/**
	* Makes the  {@code  node}  the root of the tree. In practice, this means that the value of the {@code  node}  is the first in the Euler tour
	 * @param <T>
	 * @param <T>
	* @param tree  a tree the  {@code  node}  is stored in
	* @param node  a node to make a root
	*/
	public <T> void makeRoot(AVLTree<T> tree, Node node) {
		if (node.arcs.isEmpty()) {
			return;
		}
		makeFirstArc(tree, node.getFirst());

	}

	/**
	* Makes the  {@code  arc}  the last arc of the  {@code  node}  according to the Euler tour
	 * @param <T>
	* @param tree  corresponding binary tree the Euler tour is stored in
	* @param node  a new root node
	* @param arc  an arc incident to the  {@code  node}
	*/
	public <T> void makeLastArc(AVLTree<T> tree, Node node, Arc arc) {
		if (node.arcs.size() == 1) {
			makeRoot(tree, node);
		} else {
			Arc nextArc = node.getNextArc(arc);
			makeFirstArc(tree, nextArc);
		}
	}

	/**
	* Makes the  {@code  arc}  the first arc traversed by the Euler tour
	 * @param <T>
	* @param tree  corresponding binary tree the Euler tour is stored in
	* @param object  an arc to use for tree re-rooting
	*/
	public <T> void makeFirstArc(AVLTree<T> tree, Arc object) {
		AVLTree<T> right = tree.splitBefore(object.arcTreeNode);
		tree.mergeBefore(right);
	}
}