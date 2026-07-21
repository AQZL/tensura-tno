package com.tensura_tno.client.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoreSkillsElfariaVfxOptimizerTest {

    @Test
    void distanceAndParticleSettingsSelectProgressivelyCheaperDetail() {
        assertEquals(
                MoreSkillsElfariaVfxOptimizer.Detail.NEAR,
                MoreSkillsElfariaVfxOptimizer.detailForDistanceSquared(64.0D * 64.0D, 0)
        );
        assertEquals(
                MoreSkillsElfariaVfxOptimizer.Detail.MID,
                MoreSkillsElfariaVfxOptimizer.detailForDistanceSquared(64.0D * 64.0D + 1.0D, 0)
        );
        assertEquals(
                MoreSkillsElfariaVfxOptimizer.Detail.FAR,
                MoreSkillsElfariaVfxOptimizer.detailForDistanceSquared(160.0D * 160.0D + 1.0D, 0)
        );
        assertEquals(
                MoreSkillsElfariaVfxOptimizer.Detail.MID,
                MoreSkillsElfariaVfxOptimizer.detailForDistanceSquared(0.0D, 1)
        );
        assertEquals(
                MoreSkillsElfariaVfxOptimizer.Detail.FAR,
                MoreSkillsElfariaVfxOptimizer.detailForDistanceSquared(0.0D, 2)
        );
    }

    @Test
    void meshBudgetsPreserveShapeWhileBoundingVertexCounts() {
        assertMeshBudget(MoreSkillsElfariaVfxOptimizer.Detail.ORIGINAL, 18, 144, 18, 26, 280, 96);
        assertMeshBudget(MoreSkillsElfariaVfxOptimizer.Detail.NEAR, 12, 96, 12, 18, 180, 64);
        assertMeshBudget(MoreSkillsElfariaVfxOptimizer.Detail.MID, 8, 64, 8, 12, 96, 40);
        assertMeshBudget(MoreSkillsElfariaVfxOptimizer.Detail.FAR, 4, 36, 4, 8, 48, 20);

        MoreSkillsElfariaVfxOptimizer.MeshBudget near = MoreSkillsElfariaVfxOptimizer.meshBudget(
                MoreSkillsElfariaVfxOptimizer.Detail.NEAR
        );
        assertEquals(4_608, near.discRings() * near.discSegments() * 4);
        assertEquals(864, near.rectUSteps() * near.rectVSteps() * 4);
    }

    @Test
    void instanceBudgetsKeepSingleCastsIntactAndBoundStacking() {
        assertEquals(1, MoreSkillsElfariaVfxOptimizer.renderBudget("Cathedral", 0));
        assertEquals(40, MoreSkillsElfariaVfxOptimizer.renderBudget("Strike", 0));
        assertEquals(30, MoreSkillsElfariaVfxOptimizer.renderBudget("Strike", 1));
        assertEquals(20, MoreSkillsElfariaVfxOptimizer.renderBudget("Strike", 2));
        assertEquals(48, MoreSkillsElfariaVfxOptimizer.renderBudget("Step", 0));
        assertEquals(36, MoreSkillsElfariaVfxOptimizer.renderBudget("Step", 1));
        assertEquals(24, MoreSkillsElfariaVfxOptimizer.renderBudget("Step", 2));
        assertEquals(Integer.MAX_VALUE, MoreSkillsElfariaVfxOptimizer.renderBudget("Unknown", 0));
    }

    @Test
    void conservativeBoundsContainTheLargestKnownVisualGeometry() {
        double chargeRadius = 10.0D;
        double phoenixRadius = 18.0D;
        double flightRadius = 18.0D;

        assertTrue(MoreSkillsElfariaVfxOptimizer.effectExtent("Charge", chargeRadius)
                >= chargeRadius * 19.5D);
        assertTrue(MoreSkillsElfariaVfxOptimizer.effectExtent("Phoenix", phoenixRadius)
                >= phoenixRadius * 38.5D);
        assertTrue(MoreSkillsElfariaVfxOptimizer.effectExtent("Flight", flightRadius)
                >= flightRadius * 30.0D);
        assertEquals(
                Double.POSITIVE_INFINITY,
                MoreSkillsElfariaVfxOptimizer.effectExtent("Unknown", 1.0D)
        );
    }

    private static void assertMeshBudget(
            MoreSkillsElfariaVfxOptimizer.Detail detail,
            int discRings,
            int discSegments,
            int rectUSteps,
            int rectVSteps,
            int discLines,
            int sheenLines
    ) {
        MoreSkillsElfariaVfxOptimizer.MeshBudget budget = MoreSkillsElfariaVfxOptimizer.meshBudget(detail);
        assertEquals(discRings, budget.discRings());
        assertEquals(discSegments, budget.discSegments());
        assertEquals(rectUSteps, budget.rectUSteps());
        assertEquals(rectVSteps, budget.rectVSteps());
        assertEquals(discLines, budget.discLines());
        assertEquals(sheenLines, budget.sheenLines());
        assertEquals(0, budget.discSegments() % 4);
    }
}
