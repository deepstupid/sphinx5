/*
 * 
 * Copyright 1999-2004 Carnegie Mellon University.  
 * Portions Copyright 2004 Sun Microsystems, Inc.  
 * Portions Copyright 2004 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.decoder.search;

import edu.cmu.sphinx.decoder.scorer.Scoreable;
import edu.cmu.sphinx.linguist.WordSearchState;
import edu.cmu.sphinx.linguist.dictionary.Word;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Integer;
import org.eclipse.collections.impl.bag.mutable.HashBag;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A factory for WordActiveList. The word active list is active list designed to hold word tokens only. In addition to
 * the usual active list properties such as absolute and relative beams, the word active list allows restricting the
 * number of copies of any particular word in the word beam.  Also the word active list can restrict the number of
 * fillers in the beam.
 */
public class WordActiveListFactory extends ActiveListFactory {

    /** property that sets the max paths for a single word. (zero disables this feature) */
    @S4Integer(defaultValue = 0)
    public final static String PROP_MAX_PATHS_PER_WORD = "maxPathsPerWord";

    /** property that sets the max filler words allowed in the beam. (zero disables this feature) */
    @S4Integer(defaultValue = 1)
    public final static String PROP_MAX_FILLER_WORDS = "maxFillerWords";

    private int maxPathsPerWord;
    private int maxFiller;

    /**
     * Create factory for word active list
     * @param absoluteBeamWidth beam for absolute pruning
     * @param relativeBeamWidth beam for relative pruning
     * @param maxPathsPerWord maximum number of path to keep per word
     * @param maxFiller maximum number of fillers
     */
    public WordActiveListFactory(int absoluteBeamWidth,
            double relativeBeamWidth, int maxPathsPerWord, int maxFiller )
    {
        super(absoluteBeamWidth, relativeBeamWidth);
        this.maxPathsPerWord = maxPathsPerWord;
        this.maxFiller = maxFiller;
    }

    public WordActiveListFactory() {
    }


    /*
    * (non-Javadoc)
    *
    * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
    */
    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        super.newProperties(ps);

        maxPathsPerWord = ps.getInt(PROP_MAX_PATHS_PER_WORD);
        maxFiller = ps.getInt(PROP_MAX_FILLER_WORDS);
    }


    /*
    * (non-Javadoc)
    *
    * @see edu.cmu.sphinx.decoder.search.ActiveListFactory#newInstance()
    */
    @Override
    public ActiveList newInstance() {
        return new WordActiveList();
    }


    /**
     * An active list that manages words. Guarantees only one version of a word.
     * <p>
     * <p>
     * Note that all scores are maintained in the LogMath log domain
     */
    class WordActiveList implements ActiveList {

        private Token bestToken;
        private List<Token> tokenList = new LinkedList<>();


        /**
         * Adds the given token to the list
         *
         * @param token the token to add
         */
        public boolean add(Token token) {
            tokenList.add(token);
            if (bestToken == null || token.score() > bestToken.score()) {
                bestToken = token;
            }
            return true;
        }


        /**
         * Replaces an old token with a new token
         *
         * @param oldToken the token to replace (or null in which case, replace works like add).
         * @param newToken the new token to be placed in the list.
         */
        public void replace(Token oldToken, Token newToken) {
            add(newToken);
            if (oldToken != null) {
                tokenList.remove(oldToken);
            }
        }


        /**
         * Purges excess members. Remove all nodes that fall below the relativeBeamWidth
         *
         * @return a (possible new) active list
         */

        public ActiveList commit() {
            int fillerCount = 0;

            Collections.sort(tokenList, Scoreable::compareTo);
            // remove word duplicates
            HashBag<Word> countMap = new HashBag<>();

            for (Iterator<Token> i = tokenList.iterator(); i.hasNext();) {
                Token token = i.next();
                WordSearchState wordState = (WordSearchState)token.getSearchState();

                Word word = wordState.getPronunciation().getWord();

                // only allow  maxFiller words
                if (maxFiller > 0) {
                    if (word.filler) {
                        if (fillerCount < maxFiller) {
                            fillerCount++;
                        } else {
                            i.remove();
                            continue;
                        }
                    }
                }

                if (maxPathsPerWord > 0) {
                    int c = countMap.occurrencesOf(word);


                    // Since the tokens are sorted by score we only
                    // keep the n tokens for a particular word

                    if (c < maxPathsPerWord - 1) {
                        countMap.add(word);
                    } else {
                        i.remove();
                    }
                }
            }

            if (tokenList.size() > absoluteBeamWidth) {
                tokenList = tokenList.subList(0, absoluteBeamWidth);
            }

            return this;
        }

        @Override
        public float worstScore() {
            throw new UnsupportedOperationException("yet");
        }

        /**
         * Retrieves the iterator for this tree.
         *
         * @return the iterator for this token list
         */
        public Iterator<Token> iterator() {
            return tokenList.iterator();
        }


        /**
         * Gets the set of all tokens
         *
         * @return the set of tokens
         */
        public Iterable<Token> getTokens() {
            return tokenList;
        }


        /**
         * Returns the number of tokens on this active list
         *
         * @return the size of the active list
         */
        public final int size() {
            return tokenList.size();
        }


        /**
         * gets the beam threshold best upon the best scoring token
         *
         * @return the beam threshold
         */
        public float getBeamThreshold() {
            return bestScore() + logRelativeBeamWidth;
        }


        /**
         * gets the best score in the list
         *
         * @return the best score
         */
        public float bestScore() {
            float bestScore = -Float.MAX_VALUE;
            if (bestToken != null) {
                bestScore = bestToken.score();
            }
            return bestScore;
        }


        /**
         * Sets the best scoring token for this active list
         *
         * @param token the best scoring token
         */
        public void setBestToken(Token token) {
            bestToken = token;
        }


        /**
         * Gets the best scoring token for this active list
         *
         * @return the best scoring token
         */
        public Token best() {
            return bestToken;
        }


        /* (non-Javadoc)
        * @see edu.cmu.sphinx.decoder.search.ActiveList#createNew()
        */
        public ActiveList newInstance() {
            return WordActiveListFactory.this.newInstance();
        }
    }
}
