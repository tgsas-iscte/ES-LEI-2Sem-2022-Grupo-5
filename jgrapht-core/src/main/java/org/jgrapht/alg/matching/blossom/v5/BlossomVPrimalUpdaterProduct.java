package org.jgrapht.alg.matching.blossom.v5;


public class BlossomVPrimalUpdaterProduct {
	/**
	* Performs grow operation. This is invoked on the plus-infinity  {@code  growEdge} , which connects a "+" node in the tree and an infinity matched node. The  {@code  growEdge}  and the matched free edge are added to the tree structure. Two new nodes are added to the tree: minus node and plus node. Let's call the node incident to the  {@code  growEdge}  and opposite to the minusNode the "tree node". <p> As the result, following actions are performed: <ul> <li>Add new child to the children of tree node and minus node</li> <li>Set parent edges of minus and plus nodes</li> <li>If minus node is a blossom, add it to the heap of "-" blossoms</li> <li>Remove growEdge from the heap of infinity edges</li> <li>Remove former infinity edges and add new (+, +) in-tree and cross-tree edges, (+, -) cross tree edges to the appropriate heaps (due to the changes of the labels of the minus and plus nodes)</li> <li>Add new infinity edge from the plus node</li> <li>Add new tree edges is necessary</li> <li>Subtract tree.eps from the slacks of all edges incident to the minus node</li> <li>Add tree.eps to the slacks of all edges incident to the plus node</li> </ul> <p> If the  {@code  manyGrows}  flag is true, performs recursive growing of the tree.
	* @param growEdge  the tight edge between node in the tree and minus node
	* @param recursiveGrow  specifies whether to perform recursive growing
	* @param immediateAugment  a flag that indicates whether to perform immediate augmentation if a tight (+, +) cross-tree edge is encountered
	*/
	public void grow(BlossomVEdge growEdge, boolean recursiveGrow, boolean immediateAugment,BlossomVPrimalUpdater s) {
		if (KolmogorovWeightedPerfectMatching.DEBUG) {
			System.out.println("Growing edge " + growEdge);
		}
		long start = System.nanoTime();
		int initialTreeNum = s.state.treeNum;
		int dirToMinusNode = growEdge.head[0].isInfinityNode() ? 0 : 1;
		BlossomVNode nodeInTheTree = growEdge.head[1 - dirToMinusNode];
		BlossomVNode minusNode = growEdge.head[dirToMinusNode];
		BlossomVNode plusNode = minusNode.getOppositeMatched();
		nodeInTheTree.addChild(minusNode, growEdge, true);
		minusNode.addChild(plusNode, minusNode.matched, true);
		BlossomVNode stop = plusNode;
		while (true) {
			minusNode.label = BlossomVNode.Label.MINUS;
			plusNode.label = BlossomVNode.Label.PLUS;
			minusNode.isMarked = plusNode.isMarked = false;
			processMinusNodeGrow(minusNode);
			processPlusNodeGrow(plusNode, recursiveGrow, immediateAugment,s);
			if (initialTreeNum != s.state.treeNum) {
				break;
			}
			if (plusNode.firstTreeChild != null) {
				minusNode = plusNode.firstTreeChild;
				plusNode = minusNode.getOppositeMatched();
			} else {
				while (plusNode != stop && plusNode.treeSiblingNext == null) {
					plusNode = plusNode.getTreeParent();
				}
				if (plusNode.isMinusNode()) {
					minusNode = plusNode.treeSiblingNext;
					plusNode = minusNode.getOppositeMatched();
				} else {
					break;
				}
			}
		}
		s.state.statistics.growTime += System.nanoTime() - start;
	}

	public BlossomVNode.IncidentEdgeIterator iterator(BlossomVNode blossom) {
		BlossomVTree tree = blossom.tree;
		double eps = tree.eps;
		blossom.dual -= eps;
		for (BlossomVNode.IncidentEdgeIterator iterator = blossom.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode penultimateChild = edge.headOriginal[1 - iterator.getDir()]
					.getPenultimateBlossomAndFixBlossomGrandparent();
			edge.moveEdgeTail(blossom, penultimateChild);
		}
		return iterator(null);
	}

	/**
	* Processes a minus node in the grow operation. Applies lazy delta spreading, adds new (-,+) cross-tree edges, removes former (+, inf) edges.
	* @param minusNode  a minus endpoint of the matched edge that is being appended to the tree
	*/
	public void processMinusNodeGrow(BlossomVNode minusNode) {
		double eps = minusNode.tree.eps;
		minusNode.dual += eps;
		if (minusNode.isBlossom) {
			minusNode.tree.addMinusBlossom(minusNode);
		}
		for (BlossomVNode.IncidentEdgeIterator iterator = minusNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			edge.slack -= eps;
			if (opposite.isPlusNode()) {
				if (opposite.tree != minusNode.tree) {
					if (opposite.tree.currentEdge == null) {
						BlossomVTree.addTreeEdge(minusNode.tree, opposite.tree);
					}
					opposite.tree.removePlusInfinityEdge(edge);
					opposite.tree.currentEdge.addToCurrentMinusPlusHeap(edge, opposite.tree.currentDirection);
				} else if (opposite != minusNode.getOppositeMatched()) {
					minusNode.tree.removePlusInfinityEdge(edge);
				}
			}
		}
	}

	/**
	* Expands a minus node from the odd branch. Changes the slacks of inner (-,-) and (-, inf) edges.
	* @param minusNode  a "-" node from the even branch
	*/
	public void expandMinusNode(BlossomVNode minusNode) {
		double eps = minusNode.tree.eps;
		minusNode.dual += eps;
		if (minusNode.isBlossom) {
			minusNode.tree.addMinusBlossom(minusNode);
		}
		for (BlossomVNode.IncidentEdgeIterator iterator = minusNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			if (opposite.isMarked && !opposite.isPlusNode()) {
				edge.slack -= eps;
			}
		}
	}

	/**
	* Processes a plus node on an odd circuit in the shrink operation. Moves endpoints of the boundary edges, updates slacks of incident edges.
	* @param plusNode  a plus node from an odd circuit
	* @param blossom  a newly created pseudonode
	* @return  a tight (+, +) cross-tree edge if it is encountered, null otherwise
	*/
	public BlossomVEdge shrinkPlusNode(BlossomVNode plusNode, BlossomVNode blossom) {
		BlossomVEdge augmentEdge = null;
		BlossomVTree tree = plusNode.tree;
		double eps = tree.eps;
		plusNode.dual += eps;
		for (BlossomVNode.IncidentEdgeIterator iterator = plusNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			if (!opposite.isMarked) {
				edge.moveEdgeTail(plusNode, blossom);
				if (opposite.tree != tree && opposite.isPlusNode() && edge.slack <= eps + opposite.tree.eps) {
					augmentEdge = edge;
				}
			} else if (opposite.isPlusNode()) {
				if (!opposite.isProcessed) {
					tree.removePlusPlusEdge(edge);
				}
				edge.slack -= eps;
			}
		}
		return augmentEdge;
	}

	/**
	* Processes a plus node during the grow operation. Applies lazy delta spreading, removes former (+, inf) edges, adds new (+, +) in-tree and cross-tree edges, new (+, -) cross-tree edges. When the  {@code  manyGrows}  flag is on, collects the tight (+, inf) edges on grows them as well. <p> <b>Note:</b> the recursive grows must be done ofter the grow operation on the current edge is over. This ensures correct state of the heaps and the edges' slacks.
	* @param node  a plus endpoint of the matched edge that is being appended to the tree
	* @param recursiveGrow  a flag that indicates whether to grow the tree recursively
	* @param immediateAugment  a flag that indicates whether to perform immediate augmentation if a tight (+, +) cross-tree edge is encountered
	*/
	public void processPlusNodeGrow(BlossomVNode node, boolean recursiveGrow, boolean immediateAugment, BlossomVPrimalUpdater s) {
		double eps = node.tree.eps;
		node.dual -= eps;
		BlossomVEdge augmentEdge = null;
		for (BlossomVNode.IncidentEdgeIterator iterator = node.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			edge.slack += eps;
			if (opposite.isPlusNode()) {
				if (opposite.tree == node.tree) {
					node.tree.removePlusInfinityEdge(edge);
					node.tree.addPlusPlusEdge(edge);
				} else {
					if (opposite.tree.currentEdge == null) {
						BlossomVTree.addTreeEdge(node.tree, opposite.tree);
					}
					opposite.tree.removePlusInfinityEdge(edge);
					opposite.tree.currentEdge.addPlusPlusEdge(edge);
					if (edge.slack <= node.tree.eps + opposite.tree.eps) {
						augmentEdge = edge;
					}
				}
			} else if (opposite.isMinusNode()) {
				if (opposite.tree != node.tree) {
					if (opposite.tree.currentEdge == null) {
						BlossomVTree.addTreeEdge(node.tree, opposite.tree);
					}
					opposite.tree.currentEdge.addToCurrentPlusMinusHeap(edge, opposite.tree.currentDirection);
				}
			} else if (opposite.isInfinityNode()) {
				node.tree.addPlusInfinityEdge(edge);
				if (recursiveGrow && edge.slack <= eps && !edge.getOpposite(node).isMarked) {
					if (KolmogorovWeightedPerfectMatching.DEBUG) {
						System.out.println("Growing edge " + edge);
					}
					BlossomVNode minusNode = edge.getOpposite(node);
					BlossomVNode plusNode = minusNode.getOppositeMatched();
					minusNode.isMarked = plusNode.isMarked = true;
					node.addChild(minusNode, edge, true);
					minusNode.addChild(plusNode, minusNode.matched, true);
				}
			}
		}
		if (immediateAugment && augmentEdge != null) {
			if (KolmogorovWeightedPerfectMatching.DEBUG) {
				System.out.println("Bingo grow");
			}
			 s.augment(augmentEdge);
		}
		s.state.statistics.growNum++;
	}

	/**
	* Changes dual information of the  {@code  plusNode}  and edge incident to it. This method relies on the labeling produced by the first traversal of the {@link BlossomVPrimalUpdater#expandEvenBranch(BlossomVNode,BlossomVNode,BlossomVNode)}  and on the isProcessed flags of the nodes on the even branch that have been traversed already. It also assumes that all blossom nodes are marked. <p> Since one of endpoints of the edges previously incident to the blossom changes its label, we have to update the slacks of the boundary edges incindent to the  {@code  plusNode} .
	* @param plusNode  the "+" node from the even branch
	* @return  a tight (+, +) cross-tree edge if it is encountered, null otherwise
	*/
	public BlossomVEdge expandPlusNode(BlossomVNode plusNode) {
		BlossomVEdge augmentEdge = null;
		double eps = plusNode.tree.eps;
		plusNode.dual -= eps;
		for (BlossomVNode.IncidentEdgeIterator iterator = plusNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			if (opposite.isMarked && opposite.isPlusNode()) {
				if (!opposite.isProcessed) {
					edge.slack += 2 * eps;
				}
			} else if (!opposite.isMarked) {
				edge.slack += 2 * eps;
			} else if (!opposite.isMinusNode()) {
				edge.slack += eps;
			}
			if (opposite.isPlusNode()) {
				if (opposite.tree == plusNode.tree) {
					if (!opposite.isProcessed) {
						plusNode.tree.addPlusPlusEdge(edge);
					}
				} else {
					opposite.tree.currentEdge.removeFromCurrentMinusPlusHeap(edge);
					opposite.tree.currentEdge.addPlusPlusEdge(edge);
					if (edge.slack <= eps + opposite.tree.eps) {
						augmentEdge = edge;
					}
				}
			} else if (opposite.isMinusNode()) {
				if (opposite.tree != plusNode.tree) {
					if (opposite.tree.currentEdge == null) {
						BlossomVTree.addTreeEdge(plusNode.tree, opposite.tree);
					}
					opposite.tree.currentEdge.addToCurrentPlusMinusHeap(edge, opposite.tree.currentDirection);
				}
			} else {
				plusNode.tree.addPlusInfinityEdge(edge);
			}
		}
		return augmentEdge;
	}

	/**
	* Processes a minus node from an odd circuit in the shrink operation. Moves the endpoints of the boundary edges, updates their slacks
	* @param minusNode  a minus node from an odd circuit
	* @param blossom  a newly create pseudonode
	*/
	public void shrinkMinusNode(BlossomVNode minusNode, BlossomVNode blossom) {
		BlossomVTree tree = minusNode.tree;
		double eps = tree.eps;
		minusNode.dual -= eps;
		for (BlossomVNode.IncidentEdgeIterator iterator = minusNode.incidentEdgesIterator(); iterator.hasNext();) {
			BlossomVEdge edge = iterator.next();
			BlossomVNode opposite = edge.head[iterator.getDir()];
			BlossomVTree oppositeTree = opposite.tree;
			if (!opposite.isMarked) {
				edge.moveEdgeTail(minusNode, blossom);
				edge.slack += 2 * eps;
				if (opposite.tree == tree) {
					if (opposite.isPlusNode()) {
						tree.addPlusPlusEdge(edge);
					}
				} else {
					if (opposite.isPlusNode()) {
						oppositeTree.currentEdge.removeFromCurrentMinusPlusHeap(edge);
						oppositeTree.currentEdge.addPlusPlusEdge(edge);
					} else if (opposite.isMinusNode()) {
						if (oppositeTree.currentEdge == null) {
							BlossomVTree.addTreeEdge(tree, oppositeTree);
						}
						oppositeTree.currentEdge.addToCurrentPlusMinusHeap(edge, oppositeTree.currentDirection);
					} else {
						tree.addPlusInfinityEdge(edge);
					}
				}
			} else if (opposite.isMinusNode()) {
				edge.slack += eps;
			}
		}
	}

	public BlossomVEdge edge(BlossomVTree tree, double eps, BlossomVNode node,
			BlossomVNode.IncidentEdgeIterator incidentEdgeIterator) {
		BlossomVEdge edge = incidentEdgeIterator.next();
		int dir = incidentEdgeIterator.getDir();
		BlossomVNode opposite = edge.head[dir];
		BlossomVTree oppositeTree = opposite.tree;
		if (node.isPlusNode()) {
			edge.slack -= eps;
			if (oppositeTree != null && oppositeTree != tree) {
				BlossomVTreeEdge treeEdge = oppositeTree.currentEdge;
				if (opposite.isPlusNode()) {
					treeEdge.removeFromPlusPlusHeap(edge);
					oppositeTree.addPlusInfinityEdge(edge);
				} else if (opposite.isMinusNode()) {
					treeEdge.removeFromCurrentPlusMinusHeap(edge);
				}
			}
		} else {
			edge.slack += eps;
			if (oppositeTree != null && oppositeTree != tree && opposite.isPlusNode()) {
				BlossomVTreeEdge treeEdge = oppositeTree.currentEdge;
				treeEdge.removeFromCurrentMinusPlusHeap(edge);
				oppositeTree.addPlusInfinityEdge(edge);
			}
		}
		return edge;
	}

	/**
	* Converts a tree into a set of free matched edges. Changes the matching starting from {@code  firstNode}  all the way up to the firstNode.tree.root. It changes the labeling of the nodes, applies lazy delta spreading, updates edges' presence in the heaps. This method also deletes unnecessary tree edges. <p> This method doesn't change the nodes and edge contracted in the blossoms.
	* @param firstNode  an endpoint of the  {@code  augmentEdge}  which belongs to the tree to augment
	* @param augmentEdge  a tight (+, +) cross tree edge
	*/
	public void augmentBranch(BlossomVNode firstNode, BlossomVEdge augmentEdge,BlossomVPrimalUpdater s) {
		BlossomVTree tree = firstNode.tree;
		double eps = tree.eps;
		BlossomVNode root = tree.root;
		tree.setCurrentEdges();
		for (BlossomVTree.TreeNodeIterator treeNodeIterator = tree.treeNodeIterator(); treeNodeIterator.hasNext();) {
			BlossomVNode node = treeNodeIterator.next();
			if (!node.isMarked) {
				if (node.isPlusNode()) {
					node.dual += eps;
				} else {
					node.dual -= eps;
				}
				for (BlossomVNode.IncidentEdgeIterator incidentEdgeIterator = node
						.incidentEdgesIterator(); incidentEdgeIterator.hasNext();) {
					BlossomVEdge edge = edge(tree, eps, node, incidentEdgeIterator);
				}
				node.label = BlossomVNode.Label.INFINITY;
			} else {
				node.isMarked = false;
			}
		}
		for (BlossomVTree.TreeEdgeIterator treeEdgeIterator = tree.treeEdgeIterator(); treeEdgeIterator.hasNext();) {
			BlossomVTreeEdge treeEdge = treeEdgeIterator.next();
			int dir = treeEdgeIterator.getCurrentDirection();
			BlossomVTree opposite = treeEdge.head[dir];
			opposite.currentEdge = null;
			opposite.plusPlusEdges.meld(treeEdge.plusPlusEdges);
			opposite.plusPlusEdges.meld(treeEdge.getCurrentMinusPlusHeap(dir));
			treeEdge.removeFromTreeEdgeList();
		}
		BlossomVEdge matchedEdge = augmentEdge;
		BlossomVNode plusNode = firstNode;
		BlossomVNode minusNode = plusNode.getTreeParent();
		while (minusNode != null) {
			plusNode.matched = matchedEdge;
			matchedEdge = minusNode.parentEdge;
			minusNode.matched = matchedEdge;
			plusNode = minusNode.getTreeParent();
			minusNode = plusNode.getTreeParent();
		}
		root.matched = matchedEdge;
		root.removeFromChildList();
		root.isTreeRoot = false;
		s.state.treeNum--;
	}
}