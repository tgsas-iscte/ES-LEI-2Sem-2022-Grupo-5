package org.jgrapht.alg.shortestpath;


import java.util.List;

import org.jgrapht.alg.shortestpath.EppsteinShortestPathIterator.PathsGraphVertex;

public class EppsteinShortestPathIteratorProduct1 {
	/**
	* Builds a min-heap out of the  {@code  vertices}  list
	* @param vertices  vertices
	* @param size  size of vertices
	*/
	public static void heapify(List<PathsGraphVertex> vertices, int size) {
		for (int i = size / 2 - 1; i >= 0; i--) {
			siftDown(vertices, i, size);
		}
	}

	public static void siftDown(List<PathsGraphVertex> vertices, int i, int size) {
		int left;
		int right;
		int smaller;
		int current = i;
		while (true) {
			left = 2 * current + 1;
			right = 2 * current + 2;
			smaller = current;
			if (left < size && vertices.get(left).compareTo(vertices.get(smaller)) < 0) {
				smaller = left;
			}
			if (right < size && vertices.get(right).compareTo(vertices.get(smaller)) < 0) {
				smaller = right;
			}
			if (smaller == current) {
				break;
			}
			swap(vertices, current, smaller);
			current = smaller;
		}
	}

	public static void swap(List<PathsGraphVertex> vertices, int i, int j) {
		if (i != j) {
			PathsGraphVertex tmp = vertices.get(i);
			vertices.set(i, vertices.get(j));
			vertices.set(j, tmp);
		}
	}
}