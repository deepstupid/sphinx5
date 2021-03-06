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

package edu.cmu.sphinx.decoder.search;

import edu.cmu.sphinx.util.props.S4Boolean;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;

import java.util.function.Predicate;

/**
 * An active list is maintained as a sorted list
 * <p>
 * Note that all scores are represented in LogMath logbase
 */
public interface ActiveList extends Iterable<Token> {

    /**
     * property that sets the desired (or target) size for this active list.  This is sometimes referred to as the beam
     * size
     */
    @S4Integer(defaultValue = 2000)
    String PROP_ABSOLUTE_BEAM_WIDTH = "absoluteBeamWidth";

    /**
     * Property that sets the minimum score relative to the maximum score in the list for pruning.  Tokens with a score
     * less than relativeBeamWidth * maximumScore will be pruned from the list
     */
    @S4Double(defaultValue = 0.0)
    String PROP_RELATIVE_BEAM_WIDTH = "relativeBeamWidth";
    /**
     * Property that indicates whether or not the active list will implement 'strict pruning'.  When strict pruning is
     * enabled, the active list will not remove tokens from the active list until they have been completely scored.  If
     * strict pruning is not enabled, tokens can be removed from the active list based upon their entry scores. The
     * default setting is false (disabled).
     */

    @S4Boolean(defaultValue = true)
    String PROP_STRICT_PRUNING = "strictPruning";

    /**
     * Adds the given token to the list, keeping track of the lowest scoring token
     *
     * @param token the token to add
     * @return whether it was successfully added
     */
    boolean add(Token token);

    /**
     * Purges the active list of excess members returning a (potentially new) active list
     *
     * @return a purged active list
     */
    ActiveList commit();


    /**
     * Returns the size of this list
     *
     * @return the size
     */
    int size();



    default void forWhile(Predicate<Token> x) {
        for (Token y : this) {
            if (!x.test(y))
                return;
        }
    }

    /**
     * gets the beam threshold best upon the best scoring token
     *
     * @return the beam threshold
     */
    float getBeamThreshold();


    /**
     * gets the best score in the list
     *
     * @return the best score
     */
    float bestScore();



    /**
     * Gets the best scoring token for this active list
     *
     * @return the best scoring token
     */
    Token best();


    /**
     * Creates a new empty version of this active list with the same general properties.
     *
     * @return a new active list.
     */
    ActiveList newInstance();

    /** returns the worst score if at capacity, or -Float.MAX_VALUE if under capacity (allowing anything) */
    float worstScore();
}

