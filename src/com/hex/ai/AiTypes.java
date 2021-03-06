package com.hex.ai;

import com.hex.core.AI;

public enum AiTypes {
    GameAI, BeeAI;

    public static AI newAI(AiTypes type, int playerPos, int gridSize, int difficulty) {
        switch(type) {
        case GameAI:
            return new GameAI(playerPos);
        case BeeAI:
            int depth = difficulty;
            int beamSize = 7 - difficulty;
            return new BeeGameAI(playerPos, gridSize, depth, beamSize);
        }
        return null;
    }
}
