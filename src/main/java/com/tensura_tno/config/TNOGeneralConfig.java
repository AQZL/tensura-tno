package com.tensura_tno.config;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

public class TNOGeneralConfig extends ManasConfig {
    public General General = new General();

    @Override
    public String getFileName() {
        return "tensura_tno/general";
    }

    public static class General extends ManasSubConfig {
        @Comment("Should the Tensura configurations add Tensura TNO's skills and races on next launch?")
        public boolean refresh = true;
    }
}
