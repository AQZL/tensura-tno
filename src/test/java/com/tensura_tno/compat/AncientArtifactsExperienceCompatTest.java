package com.tensura_tno.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AncientArtifactsExperienceCompatTest {

    @Test
    void restoresAPlayerKillThatWasReducedToZero() {
        assertEquals(5, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                true, false, 5, 0));
    }

    @Test
    void preservesPositiveExperienceModifiers() {
        assertEquals(2, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                true, false, 5, 2));
        assertEquals(10, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                true, false, 5, 10));
    }

    @Test
    void doesNotOverrideCanceledDrops() {
        assertEquals(0, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                true, true, 5, 0));
    }

    @Test
    void doesNotChangeNonPlayerKills() {
        assertEquals(0, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                false, false, 5, 0));
    }

    @Test
    void doesNotCreateExperienceForEntitiesWithNoReward() {
        assertEquals(0, AncientArtifactsExperienceCompat.experienceAfterCompatibility(
                true, false, 0, 0));
    }
}
