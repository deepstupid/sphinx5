/*
 * Copyright 1999-2002 Carnegie Mellon University.
 * Portions Copyright 2002 Sun Microsystems, Inc.
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.result;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used to collapse all equivalent paths in a Lattice.  Results in a Lattices that is deterministic (no Node has
 * Edges to two or more equivalent Nodes), and minimal (no Node has Edge from two or more equivalent Nodes).
 */

public class LatticeOptimizer {

    protected final Lattice lattice;
    static final private Edge[] EmptyEdgeArray = new Edge[0];


    /**
     * Create a new Lattice optimizer
     *
     * @param lattice lattice to optimize
     */
    public LatticeOptimizer(Lattice lattice) {
        this.lattice = lattice;
    }


    /**
     * Code for optimizing Lattices.  An optimal lattice has all the same paths as the original, but with fewer nodes
     * and edges
     * <p>
     * Note that these methods are all in Lattice so that it is easy to change the definition of "equivalent" nodes and
     * edges.  For example, an equivalent node might have the same word, but start or end at a different time.
     * <p>
     * To experiment with other definitions of equivalent, just create a superclass of Lattice.
     */
    public void optimize() {
        optimizeForward();
        optimizeBackward();
    }


    /**
     * Make the Lattice deterministic, so that no node has multiple outgoing edges to equivalent nodes.
     * <p>
     * Given two edges from the same node to two equivalent nodes, replace with one edge to one node with outgoing edges
     * that are a union of the outgoing edges of the old two nodes.
     * <p>
     * A --&gt; B --&gt; C \--&gt; B' --&gt; Y
     * <p>
     * where B and B' are equivalent.
     * <p>
     * is replaced with
     * <p>
     * A --&gt; B" --&gt; C \--&gt; Y
     * <p>
     * where B" is the merge of B and B'
     * <p>
     * Note that equivalent nodes must have the same incomming edges. For example
     * <p>
     * A --&gt; B \ \ X --&gt; B'
     * <p>
     * B and B' would not be equivalent because the incomming edges are different
     */
    private void optimizeForward() {
        //System.err.println("*** Optimizing forward ***");

        boolean moreChanges = true;
        while (moreChanges) {
            moreChanges = false;
            // search for a node that can be optimized
            // note that we use getCopyOfNodes to avoid concurrent changes to nodes
            for (Node n : lattice.getCopyOfNodes()) {
                // we are iterating down a list of node before optimization
                // previous iterations may have removed nodes from the list
                // therefore we have to check that the node stiff exists
                if (lattice.hasNode(n)) {
                    moreChanges |= optimizeNodeForward(n);
                }
            }
        }
    }


    /**
     * Look for 2 "to" edges to equivalent nodes.  Replace the edges with one edge to one node that is a merge of the
     * equivalent nodes
     * <p>
     * nodes are equivalent if they have equivalent from edges, and the same label
     * <p>
     * merged nodes have a union of "from" and "to" edges
     *
     * @param n node
     * @return true if Node n required an optimize forward
     */
    private boolean optimizeNodeForward(Node n) {
        assert lattice.hasNode(n);

        List<Edge> leavingEdges = new ArrayList<>(n.getLeavingEdges());
        for (int j = 0; j < leavingEdges.size(); j++) {
            Edge e = leavingEdges.get(j);
            for (int k = j + 1; k < leavingEdges.size(); k++) {
                Edge e2 = leavingEdges.get(k);

                /*
                 * If these are not the same edge, and they point to
                 * equivalent nodes, we have a hit, return true
                 */
                assert e != e2;
                if (equivalentNodesForward(e.getToNode(), e2.getToNode())) {
                    mergeNodesAndEdgesForward(e, e2);
                    return true;
                }
            }
        }
        /*
         * return false if we did not get a hit
         */
        return false;
    }


    /**
     * nodes are equivalent forward if they have "from" edges from the same nodes, and have equivalent labels (Token,
     * start/end times)
     *
     * @param n1 first node
     * @param n2 second node
     * @return true if n1 and n2 are "equivalent forwards"
     */
    private boolean equivalentNodesForward(Node n1, Node n2) {

        //assert lattice.hasNode(n1) && lattice.hasNode(n2);

        // do the labels match?
        //    and
        // if they have different number of "from" edges they are not equivalent
        // or if there is a "from" edge with no match then the nodes are not
        // equivalent
        return equivalentNodeLabels(n1, n2) && n1.hasEquivalentEnteringEdges(n2);
    }


    /**
     * given edges e1 and e2 from node n to nodes n1 and n2
     * <p>
     * merge e1 and e2, that is, merge the scores of e1 and e2 create n' that is a merge of n1 and n2 add n' add edge e'
     * from n to n'
     * <p>
     * remove n1 and n2 and all associated edges
     *
     * @param e1 first edge
     * @param e2 second edge
     */
    private void mergeNodesAndEdgesForward(Edge e1, Edge e2) {
//        assert lattice.hasNode(e1.getFromNode());
//        assert lattice.hasEdge(e1);
//        assert lattice.hasEdge(e2);
//        assert e1.getFromNode() == e2.getFromNode();

        Node n1 = e1.getToNode(), n2 = e2.getToNode();

//        assert n1.hasEquivalentEnteringEdges(n2);
//        assert n1.getWord().equals(n2.getWord());
        
        for (Edge edge : n2.getEnteringEdges()) {
            Edge anotherEdge = n1.getEdgeFromNode(edge.getFromNode());
            assert anotherEdge != null;
            anotherEdge.setAcousticScore(
                    mergeAcousticScores(edge.getAcousticScore(),
                            anotherEdge.getAcousticScore()));
            anotherEdge.setLMScore(
                    mergeLanguageScores(edge.getLMScore(),
                            anotherEdge.getLMScore()));
        }

        // add n2's edges to n1
        for (Edge edge : n2.getLeavingEdges()) {
            Edge anotherEdge = n1.getEdgeToNode(edge.getToNode());
            if (anotherEdge == null) {
                lattice.addEdge(n1, edge.getToNode(),
                        edge.getAcousticScore(), edge.getLMScore());
            } else {
                // if we got here then n1 and n2 had edges to the same node
                // choose the edge with best score
                anotherEdge.setAcousticScore(
                        mergeAcousticScores(edge.getAcousticScore(),
                                anotherEdge.getAcousticScore()));
                anotherEdge.setLMScore(
                        mergeLanguageScores(edge.getLMScore(),
                            anotherEdge.getLMScore()));
            }
        }

        // remove n2 and all associated edges
        lattice.removeNodeAndEdges(n2);
    }


    /**
     * Minimize the Lattice deterministic, so that no node has multiple incoming edges from equivalent nodes.
     * <p>
     * Given two edges from equivalent nodes to a single nodes, replace with one edge from one node with incoming edges
     * that are a union of the incoming edges of the old two nodes.
     * <p>
     * A --&gt; B --&gt; C X --&gt; B' --/
     * <p>
     * where B and B' are equivalent.
     * <p>
     * is replaced with
     * <p>
     * A --&gt; B" --&gt; C X --/
     * <p>
     * where B" is the merge of B and B'
     * <p>
     * Note that equivalent nodes must have the same outgoing edges. For example
     * <p>
     * A --&gt; X \ \ \ A' --&gt; B
     * <p>
     * A and A' would not be equivalent because the outgoing edges are different
     */
    private void optimizeBackward() {
        //System.err.println("*** Optimizing backward ***");

        boolean moreChanges = true;
        while (moreChanges) {
            moreChanges = false;
            // search for a node that can be optimized
            // note that we use getCopyOfNodes to avoid concurrent changes to nodes
            for (Node n : lattice.getCopyOfNodes()) {
                // we are iterating down a list of node before optimization
                // previous iterations may have removed nodes from the list
                // therefore we have to check that the node stiff exists
                if (lattice.hasNode(n)) {
                    moreChanges |= optimizeNodeBackward(n);
                }
            }
        }
    }


    /**
     * Look for 2 entering edges from equivalent nodes.  Replace the edges with one edge to one new node that is a merge
     * of the equivalent nodes Nodes are equivalent if they have equivalent to edges, and the same label. Merged nodes
     * have a union of entering and leaving edges
     *
     * @param n node
     * @return true if Node n required optimizing backwards
     */
    private boolean optimizeNodeBackward(Node n) {
        Edge[] enteringEdges = n.getEnteringEdges().toArray(EmptyEdgeArray);
        int es = enteringEdges.length;
        for (int j = 0; j < es; j++) {
            Edge jj = enteringEdges[j];
            for (int k = j + 1; k < es; k++) {
                Edge kk = enteringEdges[k];

                /*
                 * If these are not the same edge, and they point to
                 * equivalent nodes, we have a hit, return true
                 */
                assert jj != kk;
                if (equivalentNodesBackward(jj.getFromNode(), kk.getFromNode())) {
                    mergeNodesAndEdgesBackward(jj, kk);
                    return true;
                }
            }
        }
        /*
         * return false if we did not get a hit
         */
        return false;
    }


    /**
     * nodes are equivalent backward if they have "to" edges to the same nodes, and have equivalent labels (Token,
     * start/end times)
     *
     * @param n1 first node
     * @param n2 second node
     * @return true if n1 and n2 are "equivalent backwards"
     */
    private boolean equivalentNodesBackward(Node n1, Node n2) {

        assert lattice.hasNode(n1);
        assert lattice.hasNode(n2);

        // do the labels match?
        if (!equivalentNodeLabels(n1, n2)) return false;

        // if they have different number of "to" edges they are not equivalent
        // or if there is a "to" edge with no match then the nodes are not equiv
        return n1.hasEquivalentLeavingEdges(n2);
    }


    /**
     * Is the contents of these Node equivalent?
     *
     * @param n1 first node
     * @param n2 second node
     * @return true if n1 and n2 have "equivalent labels"
     */
    private static boolean equivalentNodeLabels(Node n1, Node n2) {
        return (n1.getWord().equals(n2.getWord()) &&
                (n1.getBeginTime() == n2.getBeginTime() &&
                        n1.getEndTime() == n2.getEndTime()));
    }


    /**
     * given edges e1 and e2 to node n from nodes n1 and n2
     * <p>
     * merge e1 and e2, that is, merge the scores of e1 and e2 create n' that is a merge of n1 and n2 add n' add edge e'
     * from n' to n
     * <p>
     * remove n1 and n2 and all associated edges
     *
     * @param e1 first edge
     * @param e2 second edge
     */
    private void mergeNodesAndEdgesBackward(Edge e1, Edge e2) {
        assert lattice.hasNode(e1.getToNode());
        assert lattice.hasEdge(e1);
        assert lattice.hasEdge(e2);
        assert e1.getToNode() == e2.getToNode();

        Node n1 = e1.getFromNode();
        Node n2 = e2.getFromNode();

        assert n1.hasEquivalentLeavingEdges(n2);
        assert n1.getWord().equals(n2.getWord());

        for (Edge edge : n2.getLeavingEdges()) {
            Edge anotherEdge = n1.getEdgeToNode(edge.getToNode());
            assert anotherEdge != null;
            anotherEdge.setAcousticScore
                    (mergeAcousticScores(edge.getAcousticScore(), 
                            anotherEdge.getAcousticScore()));
            anotherEdge.setLMScore(mergeLanguageScores(edge.getLMScore(), 
                    anotherEdge.getLMScore()));
        }

        // add n2's "from" edges to n1
        for (Edge edge : n2.getEnteringEdges()) {
            Edge anotherEdge = n1.getEdgeFromNode(edge.getFromNode());
            if (anotherEdge == null) {
                lattice.addEdge(edge.getFromNode(), n1,
                        edge.getAcousticScore(), edge.getLMScore());
            } else {
                // if we got here then n1 and n2 had edges from the same node
                // choose the edge with best score
                anotherEdge.setAcousticScore
                        (mergeAcousticScores(edge.getAcousticScore(),
                                anotherEdge.getAcousticScore()));
                anotherEdge.setLMScore(mergeLanguageScores(edge.getLMScore(),
                        anotherEdge.getLMScore()));
            }
        }

        // remove n2 and all associated edges
        lattice.removeNodeAndEdges(n2);
    }


    /** Remove all Nodes that have no Edges to them (but not &lt;s&gt;) */
    private void removeHangingNodes() {
        for (Node n : lattice.getCopyOfNodes()) {
            if (lattice.hasNode(n)) {
                if (n == lattice.getInitialNode()) {

                } else if (n == lattice.getTerminalNode()) {

                } else {
                    if (n.getLeavingEdges().isEmpty()
                            || n.getEnteringEdges().isEmpty()) {
                        lattice.removeNodeAndEdges(n);
                        removeHangingNodes();
                        return;
                    }
                }
            }
        }
    }


    /**
     * Provides a single method to merge acoustic scores, so that changes to how acoustic score are merged can be made
     * at one point only.
     *
     * @param score1 the first acoustic score
     * @param score2 the second acoustic score
     * @return the merged acoustic score
     */
    private static double mergeAcousticScores(double score1, double score2) {
        // return lattice.getLogMath().addAsLinear(score1, score2);
        return Math.max(score1, score2);
    }


    /**
     * Provides a single method to merge language scores, so that changes to how language score are merged can be made
     * at one point only.
     *
     * @param score1 the first language score
     * @param score2 the second language score
     * @return the merged language score
     */
    private static double mergeLanguageScores(double score1, double score2) {
        // return lattice.getLogMath().addAsLinear(score1, score2);
        return Math.max(score1, score2);
    }

}
