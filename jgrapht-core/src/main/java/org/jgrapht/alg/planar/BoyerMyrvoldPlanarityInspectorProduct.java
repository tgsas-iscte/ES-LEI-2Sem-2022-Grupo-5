package org.jgrapht.alg.planar;


import java.util.function.Predicate;

import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector.Edge;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector.MergeInfo;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector.Node;
import org.jgrapht.alg.planar.BoyerMyrvoldPlanarityInspector.OuterFaceCirculator;
import org.jgrapht.util.DoublyLinkedList;

import java.util.List;
import java.util.ArrayList;

public class BoyerMyrvoldPlanarityInspectorProduct {
	/**
	* Searches a back edge which target has a height smaller than  {@code  heightMax}
	* @param current  the node to start from
	* @param heightMax  an upper bound on the height of the desired back edge
	* @param forbiddenEdge  an edge the desired edge should not be equal to
	* @return  the desired back edge or null, if no such edge exist
	*/
	public Edge searchEdge(Node current, int heightMax, Edge forbiddenEdge) {
		Predicate<Edge> isNeeded = e -> {
			if (forbiddenEdge == e) {
				return false;
			}
			return e.target.height < heightMax;
		};
		return searchEdge(current, isNeeded);
	}

	/**
	* Generically searches an edge in the subtree rooted at the  {@code  current} , which doesn't include the children of the  {@code  current}  that have beem merged to the parent biconnected component.
	* @param current  the node to start the searh from
	* @param isNeeded  the predicate which the desired edge should satisfy
	* @return  an edge which satisfies the  {@code  predicate} , or null if such an edge doesn't exist
	*/
	public Edge searchEdge(Node current, Predicate<Edge> isNeeded) {
		DoublyLinkedList<Node> sDC=current.separatedDfsChildList;
		for (Node node : sDC ) {
			Edge result = searchSubtreeDfs(node, isNeeded);
			if (result != null) {
				return result;
			}
		}
		List<Edge> bE= current.backEdges;
		for (Edge edge : bE) {
			if (isNeeded.test(edge)) {
				return edge;
			}
		}
		return null;
	}

	/**
	* Recursively searches all the subtree root at the node  {@code  start}  to find an edge satisfying the  {@code  predicate} .
	* @param start  the node to start the search from.
	* @param isNeeded  a predicate, which the desired edge should satisfy
	* @return  a desired edge, or null if no such edge exist.
	*/
	public Edge searchSubtreeDfs(Node start, Predicate<Edge> isNeeded) {
		List<Node> stack = new ArrayList<>();
		stack.add(start);
		while (!stack.isEmpty()) {
			Node current = stack.remove(stack.size() - 1);
			List<Edge> e= current.backEdges;
			for (Edge edge : e) {
				if (isNeeded.test(edge)) {
					return edge;
				}
			}
			List<Edge> tE=current.treeEdges;
			for (Edge edge : tE) {
				stack.add(edge.target);
			}
		}
		return null;
	}

	/**
	* Checks whether the biconnected component rooted at  {@code  componentRoot}  can be used to extract a Kuratowski subdivision. It can be used in the case there is one externally active node on each branch of the outer face and there is a pertinent node on the lower part of the outer face between these two externally active nodes.
	* @param componentRoot  the root of the biconnected component
	* @param v  an ancestor of the nodes in the biconnected component
	* @return  an unembedded back edge, which target is  {@code  v}  and which can be used to extract a Kuratowski subdivision, or  {@code  null}  is no such edge exist for this biconnected component
	*/
	public Edge checkComponentForFailedEdge(Node componentRoot, Node v,BoyerMyrvoldPlanarityInspector s) {
		OuterFaceCirculator firstDir = s.getExternallyActiveSuccessorOnOuterFace(componentRoot, componentRoot, v, 0);
		Node firstDirNode = firstDir.getCurrent();
		OuterFaceCirculator secondDir = s.getExternallyActiveSuccessorOnOuterFace(componentRoot, componentRoot, v, 1);
		Node secondDirNode = secondDir.getCurrent();
		if (firstDirNode != componentRoot && firstDirNode != secondDirNode) {
			Node current = firstDir.next();
			while (current != secondDirNode) {
				if (current.isPertinentWrtTo(v)) {
					return searchEdge(current, e -> e.target == v && !e.embedded);
				}
				current = firstDir.next();
			}
		}
		return null;
	}

	/**
	* Finds an unembedded back edge to  {@code  v} , which can be used to extract the Kuratowski subdivision. If the merge stack isn't empty, the last biconnected component processed by the walkdown can be used to find such an edge, because walkdown descended to that component (which means that component is pertinent) and couldn't reach a pertinent node. This can only happen by encountering externally active nodes on both branches of the traversal. Otherwise, be have look in all the child biconnected components to find an unembedded back edge. We're guided by the fact that an edge can not be embedded only in the case both traversals of the walkdown could reach all off the pertinent nodes. This in turn can happen only if both traversals get stuck on externally active nodes. <p> <b>Note:</b> not every unembedded back edge can be used to extract a Kuratowski subdivision
	* @param v  the vertex which has an unembedded back edge incident to it
	* @return  the found unembedded back edge which can be used to extract a Kuratowski subdivision
	*/
	public Edge findFailedEdge(Node v,BoyerMyrvoldPlanarityInspector s) {
		if (s.stack.isEmpty()) {
			DoublyLinkedList<Node> sDC=v.separatedDfsChildList;
			for (Node child : sDC ) {
				Node componentRoot = child.initialComponentRoot;
				Edge result = checkComponentForFailedEdge(componentRoot, v,s);
				if (result != null) {
					return result;
				}
			}
			return null;
		} else {
			MergeInfo info = (MergeInfo) s.stack.get(s.stack.size() - 1);
			return checkComponentForFailedEdge(info.child, v,s);
		}
	}
}