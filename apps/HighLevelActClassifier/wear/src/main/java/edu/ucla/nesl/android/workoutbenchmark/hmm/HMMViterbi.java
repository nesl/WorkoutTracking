package edu.ucla.nesl.android.workoutbenchmark.hmm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;

import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.ObservationDiscrete;
import be.ac.ulg.montefiore.run.jahmm.ObservationInteger;
import be.ac.ulg.montefiore.run.jahmm.OpdfInteger;
import be.ac.ulg.montefiore.run.jahmm.OpdfIntegerFactory;
import be.ac.ulg.montefiore.run.jahmm.ViterbiCalculator;

/**
 * Created by cgshen on 11/23/15.
 */
public class HMMViterbi {
    private static Hmm<ObservationInteger> hmm;


    public static void initHmm() {
        // Initialization
        hmm = new Hmm<ObservationInteger>(4, new OpdfIntegerFactory(4));

        // Start prob
        hmm.setPi(0, 0.97);
        hmm.setPi(1, 0.01);
        hmm.setPi(2, 0.01);
        hmm.setPi(3, 0.01);

        // Emission prob
        hmm.setOpdf(0, new OpdfInteger(new double[]{0.6726, 0.1922, 0.1276, 0.0075}));
        hmm.setOpdf(1, new OpdfInteger(new double[]{0.0660, 0.8751, 0.0578, 0.0011}));
        hmm.setOpdf(2, new OpdfInteger(new double[]{0.0473, 0.0528, 0.8911, 0.0088}));
        hmm.setOpdf(3, new OpdfInteger(new double[]{0.0294, 0.0159, 0.0323, 0.9224}));

        // Transition prob
        hmm.setAij(0, 0, 0.9632);
        hmm.setAij(0, 1, 0.0365);
        hmm.setAij(0, 2, 0.0003);
        hmm.setAij(0, 3, 0);
        hmm.setAij(1, 0, 0.0427);
        hmm.setAij(1, 1, 0.9573);
        hmm.setAij(1, 2, 0);
        hmm.setAij(1, 3, 0);
        hmm.setAij(2, 0, 0);
        hmm.setAij(2, 1, 0);
        hmm.setAij(2, 2, 0.9987);
        hmm.setAij(2, 3, 0.0013);
        hmm.setAij(3, 0, 0);
        hmm.setAij(3, 1, 0);
        hmm.setAij(3, 2, 0.0011);
        hmm.setAij(3, 3, 0.9989);
    }

    public static int[] HMMViterbiCalcuator(LinkedList<Integer> o) {
        List<ObservationInteger> sequence = new ArrayList<>();
        ListIterator<Integer> it = o.listIterator();
        while(it.hasNext()) {
            sequence.add(new ObservationInteger(it.next()));
        }
        ViterbiCalculator vc = new ViterbiCalculator(sequence, hmm);
        return vc.stateSequence();
    }

//    public static void main(String[] args) {
//        initHmm();
//        int[] o = {1,1,3,3,2,2,2,2,2,2};
//        System.out.println(Arrays.toString(HMMViterbiCalcuator(o)));
//    }
}
