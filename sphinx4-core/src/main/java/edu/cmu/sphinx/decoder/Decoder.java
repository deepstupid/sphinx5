/*
 * Copyright 1999-2004 Carnegie Mellon University.
 * Portions Copyright 2004 Sun Microsystems, Inc.
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.decoder;

import edu.cmu.sphinx.api.SpeechResult;
import edu.cmu.sphinx.decoder.search.SearchManager;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Integer;

import java.util.List;
import java.util.function.BiPredicate;

/** The primary decoder class */
public class Decoder<S extends SearchManager> extends AbstractDecoder<S> {

    public Decoder() {
        // Keep this or else XML configuration fails.
    }

    /** The property for the number of features to recognize at once. */
    @S4Integer(defaultValue = Integer.MAX_VALUE)
    public final static String PROP_FEATURE_BLOCK_SIZE = "featureBlockSize";
    private int featureBlockSize;

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        super.newProperties(ps);
        featureBlockSize = ps.getInt(PROP_FEATURE_BLOCK_SIZE);
    }

    /**
     * Main decoder
     *
     * @param searchManager search manager to configure search space
     * @param fireNonFinalResults should we notify about non-final results
     * @param autoAllocate automatic allocation of all componenets
     * @param resultListeners listeners to get signals
     * @param featureBlockSize frequency of notification about results
     */
    public Decoder( S searchManager, boolean fireNonFinalResults, boolean autoAllocate, List<ResultListener> resultListeners, int featureBlockSize) {
        super( searchManager, fireNonFinalResults, autoAllocate, resultListeners);
        this.featureBlockSize = featureBlockSize;
    }


    /**
     * Decode frames until recognition is complete.
     *
     * @param referenceText the reference text (or null)
     * @return a result
     */
    @Override
    public Result decode(String referenceText) {
        searchManager.startRecognition();
        Result result;
        do {
            result = searchManager.recognize(featureBlockSize);
            if (result != null) {
                result.setReferenceText(referenceText);
                fireResultListeners(result);
            }
        } while (result != null && !result.isFinal());
        searchManager.stopRecognition();
        return result;
    }

    /** runs an asynchronous decode process, invoking the callback on each result */
    public synchronized void decode(BiPredicate<Decoder<S>, SpeechResult> eachResult) {

        Result result;
        boolean running = true;
        while (running) {
            searchManager.startRecognition();

            int f = featureBlockSize;
//            for (int f = 1; f < 4; f++) {
                System.out.println("recognize(f=" + f + ')');
                result = searchManager.recognize(f /*featureBlockSize*/);
                if (result != null) {
                    //result.setReferenceText(referenceText);
                    fireResultListeners(result);

                    if (!eachResult.test(this, new SpeechResult(result))) {
                        running = false;
                    }

                }
//            }


            searchManager.stopRecognition();

        }



    }
}
