package com.hex.ai;

import com.hex.core.AI;

public enum AiTypes {
    GameAI, BeeAI;

    public static AI newAI(AiTypes aiTypes, int playerPos, int gridSize) {
        switch(aiTypes) {
        case GameAI:
            return new GameAI(playerPos);
        case BeeAI:
            return new BeeGameAI(playerPos, gridSize);
        }
        return null;
    }
}
