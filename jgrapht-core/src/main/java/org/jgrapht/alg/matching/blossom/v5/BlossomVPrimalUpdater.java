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

import static org.jgrapht.alg.matching.blossom.v5.BlossomVNode.Label.*;
import static org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching.DEBUG;

/**
 * This class is used by {@link KolmogorovWeightedPerfectMatching} for performing primal operations:
 * grow, augment, shrink and expand. This class operates on alternating trees, blossom structures,
 * and node states. It changes them after applying any primal operation. Also, this class can add
 * and subtract some values from nodes' dual variables; it never changes their actual dual
 * variables.
 * <p>
 * The augment operation is used to increase the cardinality of the matching. It is applied to a
 * tight (+, +) cross-tree edge. Its main purpose is to alter the matching on the simple path
 * between tree roots through the tight edge, destroy the previous tree structures, update the state
 * of the node, and change the presence of edges in the priority queues. This operation doesn't
 * destroy the tree structure; this technique is called <i>lazy tree structure destroying</i>. The
 * information of the nodes from the tree structure block is overwritten when a node is being added
 * to another tree. This operation doesn't change the matching in the contracted blossoms.
 * <p>
 * The grow operation is used to add new nodes to a given tree. This operation is applied only to
 * tight infinity edges. It always adds even number of nodes. This operation can grow the tree
 * recursively in the depth-first order. If it encounters a tight (+, +) cross-tree edge, it stops
 * growing and performs immediate augmentation.
 * <p>
 * The shrink operation contracts an odd node circuit and introduces a new pseudonode. It is applied
 * to tight (+, +) in-tree edges. It changes the state so than the contracted nodes don't appear in
 * the surface graph. If during the changing of the endpoints of boundary edge a tight (+, +)
 * cross-tree edge is encountered, an immediate augmentation is performed.
 * <p>
 * The expand operation brings the contracted blossom nodes to the surface graph. It is applied only
 * to a "-" blossom with zero dual variable. The operation determines the two branches of a blossom:
 * an even and an odd one. The formercontains an even number of edges and can be empty, the latter
 * contains an odd number of edges and necessarily contains at least one edge. An even branch is
 * inserted into the tree. The state of the algorithm is changes respectively (node duals, tree
 * structure, etc.). If some boundary edge in a tight (+, +) cross-tree edge, an immediate
 * augmentation is performed.
 * <p>
 * The immediate augmentations are used to speed up the algorithm. More detailed description of the
 * primal operations can be found in their Javadoc.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 * @author Timofey Chudakov
 * @see KolmogorovWeightedPerfectMatching
 * @see BlossomVDualUpdater
 */
class BlossomVPrimalUpdater<V, E>
{
    private BlossomVPrimalUpdaterProduct blossomVPrimalUpdaterProduct = new BlossomVPrimalUpdaterProduct();
	/**
     * State information needed for the algorithm
     */
    public BlossomVState<V, E> state;

    /**
     * Constructs a new instance of BlossomVPrimalUpdater
     *
     * @param state contains the graph and associated information
     */
    public BlossomVPrimalUpdater(BlossomVState<V, E> state)
    {
        this.state = state;
    }

    
    public void grow(BlossomVEdge growEdge, boolean recursiveGrow, boolean immediateAugment)
    {
        blossomVPrimalUpdaterProduct.grow(growEdge, recursiveGrow, immediateAugment,this);
    }

    /**
     * Performs augment operation. This is invoked on a tight (+, +) cross-tree edge. It increases
     * the matching by 1, converts the trees on both sides into the set of free matched edges, and
     * applies lazy delta spreading.
     * <p>
     * For each tree the following actions are performed:
     * <ul>
     * <li>Labels of all nodes change to INFINITY</li>
     * <li>tree.eps is subtracted from "-" nodes' duals and added to the "+" nodes' duals</li>
     * <li>tree.eps is subtracted from all edges incident to "+" nodes and added to all edges
     * incident to "-" nodes. Consecutively, the slacks of the (+, -) in-tree edges stay
     * unchanged</li>
     * <li>Former (-, +) and (+, +) are substituted with the (+, inf) edges (removed and added to
     * appropriate heaps).</li>
     * <li>The cardinality of the matching is increased by 1</li>
     * <li>Tree structure references are set to null</li>
     * <li>Tree roots are removed from the linked list of tree roots</li>
     * </ul>
     * <p>
     * These actions change only the surface graph. They don't change the nodes and edges in the
     * pseudonodes.
     *
     * @param augmentEdge the edge to augment
     */
    public void augment(BlossomVEdge augmentEdge)
    {
        if (DEBUG) {
            System.out.println("Augmenting edge " + augmentEdge);
        }
        long start = System.nanoTime();

        // augment trees on both sides
        for (int dir = 0; dir < 2; dir++) {
            BlossomVNode node = augmentEdge.head[dir];
            blossomVPrimalUpdaterProduct.augmentBranch(node, augmentEdge,this);
            node.matched = augmentEdge;
        }

        state.statistics.augmentTime += System.nanoTime() - start;
    }

    /**
     * Performs shrink operation. This is invoked on a tight (+, +) in-tree edge. The result of this
     * operation is the substitution of an odd circuit with a single node. This means that we
     * consider the set of nodes of odd cardinality as a single node.
     * <p>
     * In the shrink operation the following main actions are performed:
     * <ul>
     * <li>Lazy dual updates are applied to all inner edges and nodes on the circuit. Thus, the
     * inner edges and nodes in the pseudonodes have valid slacks and dual variables</li>
     * <li>The endpoints of the boundary edges are moved to the new blossom node, which has label
     * {@link BlossomVNode.Label#PLUS}
     * <li>Lazy dual updates are applied to boundary edges and newly created blossom</li>
     * <li>Children of blossom nodes are moved to the blossom, their parent edges are changed
     * respectively</li>
     * <li>The blossomSibling references are set so that they form a circular linked list</li>
     * <li>If the blossom becomes a tree root, it substitutes the previous tree's root in the linked
     * list of tree roots</li>
     * <li>Since the newly created blossom with "+" label can change the classification of edges,
     * their presence in heaps is updated</li>
     * </ul>
     *
     * @param blossomFormingEdge the tight (+, +) in-tree edge
     * @param immediateAugment a flag that indicates whether to perform immediate augmentation if a
     *        tight (+, +) cross-tree edge is encountered
     * @return the newly created blossom
     */
    public BlossomVNode shrink(BlossomVEdge blossomFormingEdge, boolean immediateAugment)
    {
        long start = System.nanoTime();
        if (DEBUG) {
            System.out.println("Shrinking edge " + blossomFormingEdge);
        }
        BlossomVNode blossomRoot = findBlossomRoot(blossomFormingEdge);
        BlossomVTree tree = blossomRoot.tree;
        /*
         * We don't actually need position of the blossom node since blossom nodes aren't stored in
         * the state.nodes array. We use blossom's position as its id for debug purposes.
         */
        BlossomVNode blossom = new BlossomVNode(state.nodeNum + state.blossomNum);
        // initialize blossom node
        blossom.tree = tree;
        blossom.isBlossom = true;
        blossom.isOuter = true;
        blossom.isTreeRoot = blossomRoot.isTreeRoot;
        blossom.dual = -tree.eps;
        if (blossom.isTreeRoot) {
            tree.root = blossom;
        } else {
            blossom.matched = blossomRoot.matched;
        }

        // mark all blossom nodes
        for (BlossomVEdge.BlossomNodesIterator iterator =
            blossomFormingEdge.blossomNodesIterator(blossomRoot); iterator.hasNext();)
        {
            iterator.next().isMarked = true;
        }

        // move edges and children, change slacks if necessary
        BlossomVEdge augmentEdge = updateTreeStructure(blossomRoot, blossomFormingEdge, blossom);

        // create circular linked list of circuit nodes
        setBlossomSiblings(blossomRoot, blossomFormingEdge);

        // reset marks of blossom nodes
        blossomRoot.isMarked = false;
        blossomRoot.isProcessed = false;
        for (BlossomVNode current = blossomRoot.blossomSibling.getOpposite(blossomRoot);
            current != blossomRoot; current = current.blossomSibling.getOpposite(current))
        {
            current.isMarked = false;
            current.isProcessed = false;
        }
        blossomRoot.matched = null; // now new blossom is matched (used when finishing the matching

        state.statistics.shrinkNum++;
        state.blossomNum++;

        state.statistics.shrinkTime += System.nanoTime() - start;
        if (augmentEdge != null && immediateAugment) {
            if (DEBUG) {
                System.out.println("Bingo shrink");
            }
            augment(augmentEdge);
        }
        return blossom;
    }

    /**
     * Performs expand operation. This is invoked on a previously contracted pseudonode. The result
     * of this operation is bringing the nodes in the blossom to the surface graph. An even branch
     * of the blossom is inserted into the tree structure. Endpoints of the edges incident to the
     * blossom are moved one layer down. The slack of the inner and boundary edges are update
     * according to the lazy delta spreading technique.
     * <p>
     * <b>Note:</b> only "-" blossoms can be expanded. At that moment their dual variables are
     * always zero. This is the reason why they don't need to be stored to compute the dual
     * solution.
     * <p>
     * In the expand operation the following actions are performed:
     * <ul>
     * <li>Endpoints of the boundary edges are updated</li>
     * <li>The matching in the blossom is changed. <b>Note:</b> the resulting matching doesn't
     * depend on the previous matching</li>
     * <li>isOuter flags are updated</li>
     * <li>node.tree are updated</li>
     * <li>Tree structure is updated including parent edges and tree children of the nodes on the
     * even branch</li>
     * <li>The endpoints of some edges change their labels to "+" => their slacks are changed
     * according to the lazy delta spreading and their presence in heaps also changes</li>
     * </ul>
     *
     * @param blossom the blossom to expand
     * @param immediateAugment a flag that indicates whether to perform immediate augmentation if a
     *        tight (+, +) cross-tree edge is encountered
     */
    public void expand(BlossomVNode blossom, boolean immediateAugment)
    {
        if (DEBUG) {
            System.out.println("Expanding blossom " + blossom);
        }
        long start = System.nanoTime();

        BlossomVNode.IncidentEdgeIterator iterator = blossomVPrimalUpdaterProduct.iterator(blossom);
		BlossomVTree tree = blossom.tree;
        blossom.tree.removeMinusBlossom(blossom); // it doesn't belong to the tree no more

        BlossomVNode branchesEndpoint =
            blossom.parentEdge.getCurrentOriginal(blossom).getPenultimateBlossom();

        if (DEBUG) {
            printBlossomNodes(branchesEndpoint);
        }

        // the node which is matched to the node from outside
        BlossomVNode blossomRoot =
            blossom.matched.getCurrentOriginal(blossom).getPenultimateBlossom();

        // mark blossom nodes
        BlossomVNode current = blossomRoot;
        do {
            current.isMarked = true;
            current = current.blossomSibling.getOpposite(current);
        } while (current != blossomRoot);

        // move all edge from blossom to penultimate children
        blossom.removeFromChildList();
        // reverse the circular blossomSibling references so that the first branch in even branch
        if (!forwardDirection(blossomRoot, branchesEndpoint)) {
            reverseBlossomSiblings(blossomRoot);
        }

        // change the matching, the labeling and the dual information on the odd branch
        tree.expandOddBranch(blossomRoot, branchesEndpoint);

        // change the matching, the labeling and dual information on the even branch
        BlossomVEdge augmentEdge = expandEvenBranch(blossomRoot, branchesEndpoint, blossom);

        // reset marks of blossom nodes
        current = blossomRoot;
        do {
            current.isMarked = false;
            current.isProcessed = false;
            current = current.blossomSibling.getOpposite(current);
        } while (current != blossomRoot);
        state.statistics.expandNum++;
        state.removedNum++;
        if (DEBUG) {
            tree.printTreeNodes();
        }
        state.statistics.expandTime += System.nanoTime() - start;

        if (immediateAugment && augmentEdge != null) {
            if (DEBUG) {
                System.out.println("Bingo expand");
            }
            augment(augmentEdge);
        }

    }

	/**
     * Expands an even branch of the blossom. Here it is assumed that the blossomSiblings are
     * directed in the way that the even branch goes from {@code blossomRoot} to
     * {@code branchesEndpoint}.
     * <p>
     * The method traverses the nodes twice: first it changes the tree structure, updates the
     * labeling and flags, adds children, and changes the matching. After that it changes the slacks
     * of the edges according to the lazy delta spreading and their presence in heaps. This
     * operation is done in two steps because the later step requires correct labeling of the nodes
     * on the branch.
     * <p>
     * <b>Note:</b> this branch may consist of only one node. In this case {@code blossomRoot} and
     * {@code branchesEndpoint} are the same nodes
     *
     * @param blossomRoot the node of the blossom which is matched from the outside
     * @param branchesEndpoint the common endpoint of the even and odd branches
     * @param blossom the node that is being expanded
     * @return a tight (+, +) cross-tree edge if it is encountered, null otherwise
     */
    private BlossomVEdge expandEvenBranch(
        BlossomVNode blossomRoot, BlossomVNode branchesEndpoint, BlossomVNode blossom)
    {
        BlossomVEdge augmentEdge = null;
        BlossomVTree tree = blossom.tree;
        blossomRoot.matched = blossom.matched;
        blossomRoot.tree = tree;
        blossomRoot.addChild(blossom.matched.getOpposite(blossomRoot), blossomRoot.matched, false);

        BlossomVNode current = blossomRoot;
        BlossomVNode prevNode = current;
        current.label = MINUS;
        current.isOuter = true;
        current.parentEdge = blossom.parentEdge;
        // first traversal. It is done from blossomRoot to branchesEndpoint, i.e. from higher
        // layers of the tree to the lower
        while (current != branchesEndpoint) {
            // process "+" node
            current = current.blossomSibling.getOpposite(current);
            current.label = PLUS;
            current.isOuter = true;
            current.tree = tree;
            current.matched = current.blossomSibling;
            BlossomVEdge prevMatched = current.blossomSibling;
            current.addChild(prevNode, prevNode.blossomSibling, false);
            prevNode = current;

            // process "-" node
            current = current.blossomSibling.getOpposite(current);
            current.label = MINUS;
            current.isOuter = true;
            current.tree = tree;
            current.matched = prevMatched;
            current.addChild(prevNode, prevNode.blossomSibling, false);
            prevNode = current;
        }
        blossom.parentEdge
            .getOpposite(branchesEndpoint).addChild(branchesEndpoint, blossom.parentEdge, false);

        // second traversal, update edge slacks and their presence in heaps
        current = blossomRoot;
        blossomVPrimalUpdaterProduct.expandMinusNode(current);
        while (current != branchesEndpoint) {
            current = current.blossomSibling.getOpposite(current);
            BlossomVEdge edge = blossomVPrimalUpdaterProduct.expandPlusNode(current);
            if (edge != null) {
                augmentEdge = edge;
            }
            current.isProcessed = true; // this is needed for correct processing of (+, +) edges
                                        // connecting two node on the branch

            current = current.blossomSibling.getOpposite(current);
            blossomVPrimalUpdaterProduct.expandMinusNode(current);
        }
        return augmentEdge;
    }

    /**
     * Updates the tree structure in the shrink operation. Moves the endpoints of the boundary edges
     * to the {@code blossom}, moves the children of the nodes on the circuit to the blossom,
     * updates edges's slacks and presence in heaps accordingly.
     *
     * @param blossomRoot the node that is matched from the outside or is a tree root
     * @param blossomFormingEdge a tight (+, +) edge
     * @param blossom the node that is being inserted into the tree structure
     * @return a tight (+, +) cross-tree edge if it is encountered, null otherwise
     */
    private BlossomVEdge updateTreeStructure(
        BlossomVNode blossomRoot, BlossomVEdge blossomFormingEdge, BlossomVNode blossom)
    {
        BlossomVEdge augmentEdge = null;
        BlossomVTree tree = blossomRoot.tree;
        /**
         * Go through every vertex in the blossom and move its child list to blossom child list.
         * Handle all blossom nodes except for the blossom root. The reason is that we can't move
         * root's correctly to the blossom until both children from the circuit are removed from the
         * its children list
         */
        for (BlossomVEdge.BlossomNodesIterator iterator =
            blossomFormingEdge.blossomNodesIterator(blossomRoot); iterator.hasNext();)
        {
            BlossomVNode blossomNode = iterator.next();
            if (blossomNode != blossomRoot) {
                if (blossomNode.isPlusNode()) {
                    // substitute varNode with the blossom in the tree structure
                    blossomNode.removeFromChildList();
                    blossomNode.moveChildrenTo(blossom);
                    BlossomVEdge edge = blossomVPrimalUpdaterProduct.shrinkPlusNode(blossomNode, blossom);
                    if (edge != null) {
                        augmentEdge = edge;
                    }
                    blossomNode.isProcessed = true;
                } else {
                    if (blossomNode.isBlossom) {
                        tree.removeMinusBlossom(blossomNode);
                    }
                    blossomNode.removeFromChildList(); // minus node have only one child and this
                                                       // child belongs to the circuit
                    blossomVPrimalUpdaterProduct.shrinkMinusNode(blossomNode, blossom);
                }
            }
            blossomNode.blossomGrandparent = blossomNode.blossomParent = blossom;
        }
        // substitute varNode with the blossom in the tree structure
        blossomRoot.removeFromChildList();
        if (!blossomRoot.isTreeRoot) {
            blossomRoot.getTreeParent().addChild(blossom, blossomRoot.parentEdge, false);
        } else {
            // substitute blossomRoot with blossom in the linked list of tree roots
            blossom.treeSiblingNext = blossomRoot.treeSiblingNext;
            blossom.treeSiblingPrev = blossomRoot.treeSiblingPrev;
            blossomRoot.treeSiblingPrev.treeSiblingNext = blossom;
            if (blossomRoot.treeSiblingNext != null) {
                blossomRoot.treeSiblingNext.treeSiblingPrev = blossom;
            }
        }
        // finally process blossomRoot
        blossomRoot.moveChildrenTo(blossom);
        BlossomVEdge edge = blossomVPrimalUpdaterProduct.shrinkPlusNode(blossomRoot, blossom);
        if (edge != null) {
            augmentEdge = edge;
        }
        blossomRoot.isTreeRoot = false;

        return augmentEdge;
    }

    /**
     * Creates a circular linked list of blossom nodes.
     * <p>
     * <b>Note:</b> this method heavily relies on the property of the
     * {@link BlossomVEdge.BlossomNodesIterator} that it returns the blossomRoot while processing
     * the first branch (with direction 0).
     *
     * @param blossomRoot the common endpoint of two branches
     * @param blossomFormingEdge a tight (+, +) in-tree edge
     */
    private void setBlossomSiblings(BlossomVNode blossomRoot, BlossomVEdge blossomFormingEdge)
    {
        // set blossom sibling nodes
        BlossomVEdge prevEdge = blossomFormingEdge;
        for (BlossomVEdge.BlossomNodesIterator iterator =
            blossomFormingEdge.blossomNodesIterator(blossomRoot); iterator.hasNext();)
        {
            BlossomVNode current = iterator.next();
            if (iterator.getCurrentDirection() == 0) {
                current.blossomSibling = prevEdge;
                prevEdge = current.parentEdge;
            } else {
                current.blossomSibling = current.parentEdge;
            }
        }
    }

    /**
     * Finds a blossom root of the circuit created by the {@code edge}. More precisely, finds an lca
     * of edge.head[0] and edge.head[1].
     *
     * @param blossomFormingEdge a tight (+, +) in-tree edge
     * @return the lca of edge.head[0] and edge.head[1]
     */
    BlossomVNode findBlossomRoot(BlossomVEdge blossomFormingEdge)
    {
        BlossomVNode root, upperBound; // need to be scoped outside of the loop
        BlossomVNode[] endPoints = new BlossomVNode[2];
        endPoints[0] = blossomFormingEdge.head[0];
        endPoints[1] = blossomFormingEdge.head[1];
        int branch = 0;
        while (true) {
            if (endPoints[branch].isMarked) {
                root = endPoints[branch];
                upperBound = endPoints[1 - branch];
                break;
            }
            endPoints[branch].isMarked = true;
            if (endPoints[branch].isTreeRoot) {
                upperBound = endPoints[branch];
                BlossomVNode jumpNode = endPoints[1 - branch];
                while (!jumpNode.isMarked) {
                    jumpNode = jumpNode.getTreeGrandparent();
                }
                root = jumpNode;
                break;
            }
            endPoints[branch] = endPoints[branch].getTreeGrandparent();
            branch = 1 - branch;
        }
        BlossomVNode jumpNode = root;
        while (jumpNode != upperBound) {
            jumpNode = jumpNode.getTreeGrandparent();
            jumpNode.isMarked = false;
        }
        clearIsMarkedAndSetIsOuter(root, blossomFormingEdge.head[0]);
        clearIsMarkedAndSetIsOuter(root, blossomFormingEdge.head[1]);

        return root;
    }

    /**
     * Traverses the nodes in the tree from {@code start} to {@code root} and sets isMarked and
     * isOuter to false
     */
    private void clearIsMarkedAndSetIsOuter(BlossomVNode root, BlossomVNode start)
    {
        while (start != root) {
            start.isMarked = false;
            start.isOuter = false;
            start = start.getTreeParent();
            start.isOuter = false;
            start = start.getTreeParent();
        }
        root.isOuter = false;
        root.isMarked = false;
    }

    /**
     * Reverses the direction of blossomSibling references
     *
     * @param blossomNode some node on an odd circuit
     */
    private void reverseBlossomSiblings(BlossomVNode blossomNode)
    {
        BlossomVEdge prevEdge = blossomNode.blossomSibling;
        BlossomVNode current = blossomNode;
        do {
            current = prevEdge.getOpposite(current);
            BlossomVEdge tmpEdge = prevEdge;
            prevEdge = current.blossomSibling;
            current.blossomSibling = tmpEdge;
        } while (current != blossomNode);
    }

    /**
     * Checks whether the direction of blossomSibling references is suitable for the expand
     * operation, i.e. an even branch goes from {@code blossomRoot} to {@code branchesEndpoint}.
     *
     * @param blossomRoot a node on an odd circuit that is matched from the outside
     * @param branchesEndpoint a node common to both branches
     * @return true if the condition described above holds, false otherwise
     */
    private boolean forwardDirection(BlossomVNode blossomRoot, BlossomVNode branchesEndpoint)
    {
        int hops = 0;
        BlossomVNode current = blossomRoot;
        while (current != branchesEndpoint) {
            ++hops;
            current = current.blossomSibling.getOpposite(current);
        }
        return (hops & 1) == 0;
    }

    /**
     * Prints {@code blossomNode} and all its blossom siblings. This method is for debug purposes.
     *
     * @param blossomNode the node to start from
     */
    public void printBlossomNodes(BlossomVNode blossomNode)
    {
        System.out.println("Printing blossom nodes");
        BlossomVNode current = blossomNode;
        do {
            System.out.println(current);
            current = current.blossomSibling.getOpposite(current);
        } while (current != blossomNode);
    }
}
