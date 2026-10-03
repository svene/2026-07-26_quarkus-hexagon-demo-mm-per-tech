package org.svenehrke.triptychdemo.cross;

import org.svenehrke.triptychdemo.cross.reorder.LearnedLevels;

/** Learned reorder point and order-up-to level of one product at one location. */
public record LevelsVM(int min, int max) {

    static LevelsVM of(LearnedLevels levels) {
        return new LevelsVM(levels.min(), levels.max());
    }
}
