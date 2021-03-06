package edu.cmu.sphinx.util.props;

import edu.cmu.sphinx.frontend.DataProcessor;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FrontEnd;
import edu.cmu.sphinx.instrumentation.AccuracyTracker;
import edu.cmu.sphinx.instrumentation.BestPathAccuracyTracker;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

/** Some unit-tests for the ConfigurationManagerUtils. */
public class CMUTests {

    @Test
    public void testClassTesting() {
        Assert.assertTrue(ConfigurationManagerUtils.isImplementingInterface(FrontEnd.class, DataProcessor.class));
        Assert.assertTrue(ConfigurationManagerUtils.isImplementingInterface(DataProcessor.class, Configurable.class));
        Assert.assertFalse(ConfigurationManagerUtils.isImplementingInterface(Configurable.class, Configurable.class));

        Assert.assertFalse(ConfigurationManagerUtils.isSubClass(Configurable.class, Configurable.class));
        Assert.assertTrue(ConfigurationManagerUtils.isSubClass(Integer.class, Object.class));
        Assert.assertFalse(ConfigurationManagerUtils.isSubClass(Object.class, Object.class));

        Assert.assertTrue(ConfigurationManagerUtils.isSubClass(BestPathAccuracyTracker.class, AccuracyTracker.class));

        Assert.assertTrue(ConfigurationManagerUtils.isDerivedClass(BestPathAccuracyTracker.class, AccuracyTracker.class));
        Assert.assertTrue(ConfigurationManagerUtils.isDerivedClass(BestPathAccuracyTracker.class, BestPathAccuracyTracker.class));
        Assert.assertTrue(!ConfigurationManagerUtils.isDerivedClass(BestPathAccuracyTracker.class, DoubleData.class));
    }


    @Test
    public void setComponentPropertyTest() throws IOException {
        //File configFile = new File("sphinx4-core/src/test/resources/edu/cmu/sphinx/util/props/ConfigurationManagerTest.testconfig.sxl");
        ConfigurationManager cm = new ConfigurationManager(
                getClass().getResource("ConfigurationManagerTest.testconfig.sxl")
                //configFile.toURI().toURL()
        );

        int newBeamWidth = 4711;
        ConfigurationManagerUtils.setProperty(cm, "beamWidth", String.valueOf(newBeamWidth));

        DummyComp dummyComp = cm.lookup("duco");
        Assert.assertEquals(newBeamWidth, dummyComp.getBeamWidth());
    }
}
