/*
 * (C) Copyright 2018-2021, by Timofey Chudakov and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * See the CONTRIBUTORS.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the
 * GNU Lesser General Public License v2.1 or later
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR LGPL-2.1-or-later
 */
package org.jgrapht.alg.matching.blossom.v5;

import org.jheaps.*;
import org.jheaps.tree.*;

import java.util.*;

/**
 * This class is a data structure for Kolmogorov's Blossom V algorithm.
 * <p>
 * Represents an alternating tree of <em>tight</em> edges which is used to find an augmenting path
 * of tight edges in order to perform an augmentation and increase the cardinality of the matching.
 * The nodes on odd layers are necessarily connected to their children via matched edges. Thus,
 * these nodes have always exactly one child. The nodes on even layers can have arbitrarily many
 * children.
 * <p>
 * The tree structure information is contained in {@link BlossomVNode}, this class only contains the
 * reference to the root of the tree. It also contains three heaps:
 * <ul>
 * <li>A heap of (+, inf) edges. These edges are also called infinity edges. If there exists a tight
 * infinity edge, then it can be grown. Thus, this heap is used to determine an infinity edge of
 * minimum slack.</li>
 * <li>A heap of (+, +) in-tree edges. These are edges between "+" nodes from the same tree. If a
 * (+, +) in-tree edges is tight, it can be used to perform the shrink operation and introduce a new
 * blossom. Thus, this heap is used to determine a (+, +) in-tree edge of minimum slack in a given
 * tree.</li>
 * <li>A heap of "-" blossoms. If there exists a blossom with zero actual dual variable, it can be
 * expanded. Thus, this heap is used to determine a "-" blossom with minimum dual variable</li>
 * </ul>
 * <p>
 * Each tree contains a variable which accumulates dual changes applied to it. The dual changes
 * aren't spread until a tree is destroyed by an augmentation. For every node in the tree its true
 * dual variable is equal to {@code node.dual + node.tree.eps} if it is a "+" node; otherwise it
 * equals {@code node.dual - node.tree.eps}. This applies only to the surface nodes that belong to
 * some tree.
 * <p>
 * This class also contains implementations of two iterators: {@link TreeEdgeIterator} and
 * {@link TreeNodeIterator}. They are used to conveniently traverse the tree edges incident to a
 * particular tree, and to traverse the nodes of a tree in a depth-first order.
 *
 * @author Timofey Chudakov
 * @see BlossomVNode
 * @see BlossomVTreeEdge
 * @see KolmogorovWeightedPerfectMatching
 */
class BlossomVTree
{
    /**
     * Variable for debug purposes
     */
    private static int currentId = 1;
    /**
     * Two-element array of the first elements in the circular doubly linked lists of incident tree
     * edges in each direction.
     */
    BlossomVTreeEdge[] first;
    /**
     * This variable is used to quickly determine the edge between two trees during primal
     * operations.
     * <p>
     * Let $T$ be a tree that is being processed in the main loop. For every tree $T'$ that is
     * adjacent to $T$ this variable is set to the {@code BlossomVTreeEdge} that connects both
     * trees. This variable also helps to indicate whether a pair of trees is adjacent or not. This
     * variable is set to {@code null} when no primal operation can be applied to the tree $T$.
     */
    BlossomVTreeEdge currentEdge;
    /**
     * Direction of the tree edge connecting this tree and the currently processed tree
     */
    int currentDirection;
    /**
     * Dual change that hasn't been spread among the nodes in this tree. This technique is called
     * lazy delta spreading
     */
    double eps;
    /**
     * Accumulated dual change. Is used during dual updates
     */
    double accumulatedEps;
    /**
     * The root of this tree
     */
    BlossomVNode root;
    /**
     * Next tree in the connected component, is used during updating the duals via connected
     * components
     */
    BlossomVTree nextTree;
    /**
     * The heap of (+,+) edges of this tree
     */
    MergeableAddressableHeap<Double, BlossomVEdge> plusPlusEdges;
    /**
     * The heap of (+, inf) edges of this tree
     */
    MergeableAddressableHeap<Double, BlossomVEdge> plusInfinityEdges;
    /**
     * The heap of "-" blossoms of this tree
     */
    MergeableAddressableHeap<Double, BlossomVNode> minusBlossoms;
    /**
     * Variable for debug purposes
     */
    int id;

    /**
     * Empty constructor
     */
    public BlossomVTree()
    {
    }

    /**
     * Constructs a new tree with the {@code root}
     *
     * @param root the root of this tree
     */
    public BlossomVTree(BlossomVNode root)
    {
        this.root = root;
        root.tree = this;
        root.isTreeRoot = true;
        first = new BlossomVTreeEdge[2];
        plusPlusEdges = new PairingHeap<>();
        plusInfinityEdges = new PairingHeap<>();
        minusBlossoms = new PairingHeap<>();
        this.id = currentId++;
    }

    /**
     * Adds a new tree edge from {@code from} to {@code to}. Sets the to.currentEdge and
     * to.currentDirection with respect to the tree {@code from}
     *
     * @param from the tail of the directed tree edge
     * @param to the head of the directed tree edge
     */
    public static BlossomVTreeEdge addTreeEdge(BlossomVTree from, BlossomVTree to)
    {
        BlossomVTreeEdge treeEdge = new BlossomVTreeEdge();

        treeEdge.head[0] = to;
        treeEdge.head[1] = from;

        if (from.first[0] != null) {
            from.first[0].prev[0] = treeEdge;
        }
        if (to.first[1] != null) {
            to.first[1].prev[1] = treeEdge;
        }

        treeEdge.next[0] = from.first[0];
        treeEdge.next[1] = to.first[1];

        from.first[0] = treeEdge;
        to.first[1] = treeEdge;

        to.currentEdge = treeEdge;
        to.currentDirection = 0;
        return treeEdge;
    }

    /**
     * Sets the currentEdge and currentDirection variables for all trees adjacent to this tree
     */
    public void setCurrentEdges()
    {
        BlossomVTreeEdge treeEdge;
        for (BlossomVTree.TreeEdgeIterator iterator = treeEdgeIterator(); iterator.hasNext();) {
            treeEdge = iterator.next();
            BlossomVTree opposite = treeEdge.head[iterator.getCurrentDirection()];
            opposite.currentEdge = treeEdge;
            opposite.currentDirection = iterator.getCurrentDirection();
        }
    }

    /**
     * Clears the currentEdge variable of all adjacent to the {@code tree} trees
     */
    public void clearCurrentEdges()
    {
        currentEdge = null;
        for (TreeEdgeIterator iterator = treeEdgeIterator(); iterator.hasNext();) {
            iterator.next().head[iterator.getCurrentDirection()].currentEdge = null;
        }
    }

    /**
     * Prints all the nodes of this tree
     */
    public void printTreeNodes()
    {
        System.out.println("Printing tree nodes");
        for (BlossomVTree.TreeNodeIterator iterator = treeNodeIterator(); iterator.hasNext();) {
            System.out.println(iterator.next());
        }
    }

    @Override
    public String toString()
    {
        return "BlossomVTree pos=" + id + ", eps = " + eps + ", root = " + root;
    }

    /**
     * Ensures correct addition of an edge to the heap
     *
     * @param edge a (+, +) edge
     */
    public void addPlusPlusEdge(BlossomVEdge edge)
    {
        edge.handle = plusPlusEdges.insert(edge.slack, edge);
    }

    /**
     * Ensures correct addition of an edge to the heap
     *
     * @param edge a (+, inf) edge
     */
    public void addPlusInfinityEdge(BlossomVEdge edge)
    {
        edge.handle = plusInfinityEdges.insert(edge.slack, edge);
    }

    /**
     * Ensures correct addition of a blossom to the heap
     *
     * @param blossom a "-" blossom
     */
    public void addMinusBlossom(BlossomVNode blossom)
    {
        blossom.handle = minusBlossoms.insert(blossom.dual, blossom);
    }

    /**
     * Removes the {@code edge} from the heap of (+, +) edges
     *
     * @param edge the edge to remove
     */
    public void removePlusPlusEdge(BlossomVEdge edge)
    {
        edge.removePlusPlusEdge();
    }

    /**
     * Removes the {@code edge} from the heap of (+, inf) edges
     *
     * @param edge the edge to remove
     */
    public void removePlusInfinityEdge(BlossomVEdge edge)
    {
        edge.handle.delete();
    }

    /**
     * Removes the {@code blossom} from the heap of "-" blossoms
     *
     * @param blossom the blossom to remove
     */
    public void removeMinusBlossom(BlossomVNode blossom)
    {
        blossom.handle.delete();
    }

    /**
     * Returns a new instance of TreeNodeIterator for this tree
     *
     * @return new TreeNodeIterator for this tree
     */
    public TreeNodeIterator treeNodeIterator()
    {
        return new TreeNodeIterator(root);
    }

    /**
     * Returns a new instance of TreeEdgeIterator for this tree
     *
     * @return new TreeEdgeIterators for this tree
     */
    public TreeEdgeIterator treeEdgeIterator()
    {
        return new TreeEdgeIterator();
    }

    /**
     * An iterator over tree nodes. This iterator traverses the nodes of the tree in a depth-first
     * order. <b>Note:</b> this iterator can also be used to iterate the nodes of some subtree of a
     * tree.
     */
    public static class TreeNodeIterator
        implements
        Iterator<BlossomVNode>
    {
        private TreeNodeIteratorProduct treeNodeIteratorProduct = new TreeNodeIteratorProduct();
		/**
         * Variable to determine whether {@code currentNode} has been returned or not
         */
        private BlossomVNode current;
        /**
         * Constructs a new TreeNodeIterator for a {@code root}.
         * <p>
         * <b>Note:</b> {@code root} doesn't need to be a root of some tree; this iterator also
         * works with subtrees.
         *
         * @param root node of a tree to start dfs traversal from.
         */
        public TreeNodeIterator(BlossomVNode root)
        {
            treeNodeIteratorProduct.setCurrentNode(this.current = root);
            treeNodeIteratorProduct.setTreeRoot(root);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext()
        {
            if (current != null) {
                return true;
            }
            current = treeNodeIteratorProduct.advance();
            return current != null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BlossomVNode next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            BlossomVNode result = current;
            current = null;
            return result;
        }
    }

    /**
     * An iterator over tree edges incident to this tree.
     */
    public class TreeEdgeIterator
        implements
        Iterator<BlossomVTreeEdge>
    {
        /**
         * The direction of the {@code currentEdge}
         */
        private int currentDirection;
        /**
         * The tree edge this iterator is currently on
         */
        private BlossomVTreeEdge currentEdge;
        /**
         * Variable to determine whether currentEdge has been returned or not
         */
        private BlossomVTreeEdge result;

        /**
         * Constructs a new TreeEdgeIterator
         */
        public TreeEdgeIterator()
        {
            currentEdge = first[0];
            currentDirection = 0;
            if (currentEdge == null) {
                currentEdge = first[1];
                currentDirection = 1;
            }
            result = currentEdge;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext()
        {
            if (result != null) {
                return true;
            }
            result = advance();
            return result != null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BlossomVTreeEdge next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            BlossomVTreeEdge res = result;
            result = null;
            return res;
        }

        /**
         * Returns the direction of the current edge
         *
         * @return the direction of the current edge
         */
        public int getCurrentDirection()
        {
            return currentDirection;
        }

        /**
         * Moves this iterator to the next tree edge. If the last outgoing edge has been traversed,
         * changes the current direction to 1. If the the last incoming edge has been traversed,
         * sets {@code currentEdge} to null.
         *
         * @return the next tree edge or null if all edges have been traversed already
         */
        private BlossomVTreeEdge advance()
        {
            if (currentEdge == null) {
                return null;
            }
            currentEdge = currentEdge.next[currentDirection];
            if (currentEdge == null && currentDirection == 0) {
                currentDirection = 1;
                currentEdge = first[1];
            }
            return currentEdge;
        }
    }

	/**
	 * Computes and returns the value which can be assigned to the  {@code  tree.eps}  so that it doesn't violate in-tree constraints. In other words,  {@code  getEps(tree) - tree.eps}  is the resulting dual change wrt. in-tree constraints. The computed value is always greater than or equal to the  {@code  tree.eps} , can violate the cross-tree constraints, and can be equal to {@link KolmogorovWeightedPerfectMatching#INFINITY} .
	 * @return  a value which can be safely assigned to tree.eps
	 */
	public double getEps() {
		double eps = KolmogorovWeightedPerfectMatching.INFINITY;
		if (!this.plusInfinityEdges.isEmpty()) {
			BlossomVEdge edge = this.plusInfinityEdges.findMin().getValue();
			if (edge.slack < eps) {
				eps = edge.slack;
			}
		}
		if (!this.minusBlossoms.isEmpty()) {
			BlossomVNode node = this.minusBlossoms.findMin().getValue();
			if (node.dual < eps) {
				eps = node.dual;
			}
		}
		if (!this.plusPlusEdges.isEmpty()) {
			BlossomVEdge edge = this.plusPlusEdges.findMin().getValue();
			if (2 * eps > edge.slack) {
				eps = edge.slack / 2;
			}
		}
		return eps;
	}

	/**
	 * Expands an infinity node from the odd branch
	 * @param infinityNode  a node from the odd branch
	 */
	public void expandInfinityNode(BlossomVNode infinityNode) {
		double eps = this.eps;
		for (BlossomVNode.IncidentEdgeIterator iterator = infinityNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			if (!opposite.isMarked) {
				edge.slack += eps;
				if (opposite.isPlusNode()) {
					if (opposite.tree != this) {
						opposite.tree.currentEdge.removeFromCurrentMinusPlusHeap(edge);
					}
					opposite.tree.addPlusInfinityEdge(edge);
				}
			}
		}
	}

	/**
	 * Expands the nodes on an odd branch. Here it is assumed that the blossomSiblings are directed in the way the odd branch goes from  {@code  branchesEndpoint}  to  {@code  blossomRoot} . <p> The method traverses the nodes only once setting the labels, flags, updating the matching, removing former (+, -) edges and creating new (+, inf) edges in the corresponding heaps. The method doesn't process the  {@code  blossomRoot}  and  {@code  branchesEndpoint}  as they belong to the even branch.
	 * @param blossomRoot  the node that is matched from the outside
	 * @param branchesEndpoint  the common node of the even and odd branches
	 */
	public void expandOddBranch(BlossomVNode blossomRoot, BlossomVNode branchesEndpoint) {
		BlossomVNode current = branchesEndpoint.blossomSibling.getOpposite(branchesEndpoint);
		while (current != blossomRoot) {
			current.label = BlossomVNode.Label.INFINITY;
			current.isOuter = true;
			current.tree = null;
			current.matched = current.blossomSibling;
			BlossomVEdge prevMatched = current.blossomSibling;
			expandInfinityNode(current);
			current = current.blossomSibling.getOpposite(current);
			current.label = BlossomVNode.Label.INFINITY;
			current.isOuter = true;
			current.tree = null;
			current.matched = prevMatched;
			expandInfinityNode(current);
			current = current.blossomSibling.getOpposite(current);
		}
	}
}
