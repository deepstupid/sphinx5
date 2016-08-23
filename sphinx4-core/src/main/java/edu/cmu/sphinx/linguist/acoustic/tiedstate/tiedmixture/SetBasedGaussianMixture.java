/*
 * Copyright 2014 Carnegie Mellon University.  
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.linguist.acoustic.tiedstate.tiedmixture;

import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.GaussianMixture;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.GaussianWeights;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.MixtureComponent;
import edu.cmu.sphinx.util.LogMath;

/**
 * Represents gaussian mixture that is based on provided mixture component set
 * <p>
 * All scores and weights are maintained in LogMath log base.
 */

@SuppressWarnings("serial")
public class SetBasedGaussianMixture extends GaussianMixture {
	
    private final MixtureComponentSet mixtureComponentSet;
    
    public SetBasedGaussianMixture(GaussianWeights mixtureWeights,
            MixtureComponentSet mixtureComponentSet, int id) {
        super(mixtureWeights, null, id);
        this.mixtureComponentSet = mixtureComponentSet;
    }

    @Override
    public float calculateScore(Data feature) {

        MixtureComponentSetScores curScores = mixtureComponentSet.updateTopScores(feature);

        float ascore = 0;

        int s = mixtureWeights.streams;
        int t = mixtureComponentSet.topGauNum;

        for (int i = 0; i < s; i++) {

            float logTotal = LogMath.LOG_ZERO;
            float[] scoreRow = curScores.scores[i];
            int[] idRow = curScores.ids[i];

            for (int j = 0; j < t; j++) {

                float gauScore = scoreRow[j];
                int gauId = idRow[j];

                logTotal = LogMath.addAsLinear(logTotal, gauScore + mixtureWeights.get(id, i, gauId));
            }

            ascore += logTotal;
        }
        return ascore;
    }

    /**
     * Calculates the scores for each component in the senone.
     *
     * @param feature the feature to score
     * @return the LogMath log scores for the feature, one for each component
     */
    @Override
    public float[] calculateComponentScore(Data feature) {
        mixtureComponentSet.updateScores(feature);
        float[] scores = new float[mixtureComponentSet.size()];
        int scoreIdx = 0;
        for (int i = 0; i < mixtureWeights.streams; i++) {
            for (int j = 0; j < mixtureComponentSet.gauNum; j++) {
                scores[scoreIdx++] = mixtureComponentSet.getGauScore(i, j) + mixtureWeights.get(id, i, mixtureComponentSet.getGauId(i, j));
            }
        }
        return scores;
    }
    
    @Override
    public MixtureComponent[] getMixtureComponents() {
        return mixtureComponentSet.toArray();
    }
    
    @Override
    public int dimension() {
        return mixtureComponentSet.dimension();
    }
    
    /** @return the number of component densities of this <code>GaussianMixture</code>. */
    @Override
    public int numComponents() {
        return mixtureComponentSet.size();
    }

}
